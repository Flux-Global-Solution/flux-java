package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.AlertaDAO;
import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.LeituraDAO;
import br.com.fiap.flux.dao.PerfilConsumoDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.AnaliseGeralResponseDTO;
import br.com.fiap.flux.dto.AnaliseHistoricoResponseDTO;
import br.com.fiap.flux.dto.AnaliseResultadoResponseDTO;
import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;
import br.com.fiap.flux.mapper.AnaliseMapper;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Leitura;
import br.com.fiap.flux.models.PerfilConsumo;
import br.com.fiap.flux.models.Residencia;
import br.com.fiap.flux.util.ConsumoUtil;
import br.com.fiap.flux.util.ConsumoUtil.Periodo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Análise simulada de consumo: faz o papel da inteligência artificial que, no futuro, vai analisar
 * as
 * leituras. Não chama nenhuma API de IA nem treina modelos: são regras fixas que comparam as
 * leituras
 * do banco com as configurações da residência e com o perfil de consumo já calculado. O alerta só
 * nasce aqui, no servidor; o front-end apenas consulta.
 *
 * <p><b>Janela de análise.</b> Os últimos {@value #JANELA_DIAS} dias, em dias inteiros, terminando
 * no dia
 * em que termina a última leitura da residência. Os dados são simulados e podem ser antigos; por
 * isso
 * a janela acompanha as leituras, e não o relógio. Rodar de novo sem leituras novas dá o mesmo
 * resultado.</p>
 *
 * <p><b>Fluxo contínuo.</b> Cada leitura é um trecho que começa em {@code dt_leitura} e dura
 * {@code nr_duracao_fluxo_seg}. Trechos do mesmo dispositivo viram um só fluxo quando o próximo
 * começa
 * até {@value #TOLERANCIA_LACUNA_SEG} s depois do fim do anterior; uma pausa maior encerra o
 * fluxo.</p>
 *
 * <p><b>Regras</b>, em ordem de prioridade (uma leitura explica no máximo uma anomalia, para não
 * haver
 * alerta duplo pelo mesmo consumo):</p>
 * <ol>
 *   <li>duração do fluxo contra os limites da residência (RN04 e RN05): fluxo acima do limite de
 *       vazamento <i>dentro</i> do período de menor consumo é {@code VAZAMENTO_COMUM} (crítico);
 *       fluxo
 *       acima do limite de ponto aberto <i>fora</i> desse período é {@code PONTO_ABERTO}. Um fluxo
 *       que
 *       atravessa a borda do período é medido em cada lado: só os minutos de dentro contam para o
 *       vazamento e só os de fora para o ponto aberto. Os dois limites não geram dois alertas: vale
 *       o de
 *       maior gravidade. O ponto aberto sobe para crítico se ocorre em período de ausência ou com
 *       vazão
 *       muito acima do padrão;</li>
 *   <li>consumo em período de ausência: {@code FORA_DO_PADRAO};</li>
 *   <li>consumo no horário de menor utilização, relevante frente ao consumo médio diário do perfil:
 *       {@code FORA_DO_PADRAO};</li>
 *   <li>fluxo com vazão média muito acima da vazão média do perfil: {@code FORA_DO_PADRAO};</li>
 *   <li>total do dia muito acima do consumo médio do perfil (média mais desvios padrão e um
 *       múltiplo
 *       da média): {@code FORA_DO_PADRAO}.</li>
 * </ol>
 * <p>As regras 3 a 5 só valem com perfil de histórico suficiente. Variação de consumo, sozinha,
 * nunca
 * vira vazamento, e só os aumentos são avaliados.</p>
 *
 * <p><b>Ocorrência e duplicidade.</b> A ocorrência é identificada por dispositivo, classe do alerta
 * e
 * momento em que começou ({@code dt_identificacao}): o início do fluxo (ponto aberto e vazamento
 * contam
 * como uma só classe, então um fluxo tem no máximo um alerta), a primeira leitura do consumo em
 * ausência
 * ou no horário de menor uso, ou o começo do dia (variação diária). Se a ocorrência já tem alerta,
 * nada
 * é criado: o alerta existente só é atualizado se ela piorou (virou vazamento, ficou crítica ou o
 * volume
 * desperdiçado aumentou) e nunca se já foi resolvido.</p>
 *
 * <p><b>Resolução automática (RN09).</b> Um alerta em aberto (aberto, reconhecido ou em reparo)
 * vira
 * resolvido quando o dispositivo segue enviando leituras e nenhuma anomalia dele aparece por 48
 * horas
 * seguidas depois do fim da última anomalia. As 48 h são contadas até a última leitura do
 * dispositivo,
 * e não até o relógio: se ele parou de enviar dados, isso não prova fluxo normal.</p>
 *
 * <p><b>Sensor em possível falha (RN13).</b> Se um dispositivo fica 48 h seguidas sem passagem de
 * água
 * em residência habitada, o sensor passa a "possível falha" ({@code sensorOk = false}). Os dias de
 * ausência informados na residência não contam: a contagem só começa no dia seguinte ao fim da
 * ausência. Quando a água volta a passar, o sensor volta a ficar ok. O "agora" também é a última
 * leitura da residência.</p>
 */
@Service
public class AnaliseConsumoService {

    public static final String SITUACAO_CRIADO = "CRIADO";
    public static final String SITUACAO_ATUALIZADO = "ATUALIZADO";
    public static final String SITUACAO_JA_EXISTENTE = "JA_EXISTENTE";
    public static final String SITUACAO_NOVO = "NOVO";

    static final int JANELA_DIAS = 7;
    // RN09: horas de fluxo normal seguidas para resolver o alerta sozinho
    static final int HORAS_FLUXO_NORMAL_PARA_RESOLVER = 48;
    // RN13: horas seguidas sem passagem de água, em residência habitada, para suspeitar do sensor
    static final int HORAS_SEM_PASSAGEM_SENSOR = 48;
    static final int TOLERANCIA_LACUNA_SEG = 60;
    // abaixo disso o consumo fora de hora é tratado como ruído do sensor, não como anomalia
    static final double VOLUME_MINIMO_LITROS = 5.0;
    // vazão do fluxo (L/min) a partir da qual ela é "muito acima" da vazão média do perfil (atenção / crítico)
    static final double FATOR_VAZAO_ALTA = 3.0;
    static final double FATOR_VAZAO_CRITICA = 5.0;
    // fluxos mais curtos que isto não são avaliados pela vazão (picos momentâneos)
    static final int DURACAO_MINIMA_VAZAO_SEG = 300;
    // parte do consumo médio diário gasta no horário de menor uso que já é suspeita (atenção / crítico)
    static final double FRACAO_NOITE_ATENCAO = 0.10;
    static final double FRACAO_NOITE_CRITICA = 0.25;
    // parte do consumo médio diário gasta em ausência que torna a anomalia crítica
    static final double FRACAO_AUSENCIA_CRITICA = 0.50;
    // variação diária crítica: precisa passar a média + N desvios padrão E um múltiplo da média
    // (o nível de atenção é o limite de PerfilConsumo.estaNoPadrao)
    static final double DESVIOS_CRITICO = 3.0;
    static final double MULTIPLO_CRITICO = 2.0;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    // evita que duas análises simultâneas criem o mesmo alerta (conferir e gravar viram uma etapa só)
    private static final Object TRAVA_GRAVACAO = new Object();

    /**
     * Sequência de trechos de um mesmo dispositivo sem pausa maior que a tolerância: o "fluxo
     * contínuo".
     */
    private static class Fluxo {
        final int idDispositivo;
        final List<Leitura> leituras = new ArrayList<>();
        LocalDateTime inicio;
        LocalDateTime fim;
        long duracaoSeg;
        double volume;

        Fluxo(int idDispositivo) {
            this.idDispositivo = idDispositivo;
        }

        /**
         * Soma o trecho ao fluxo sem contar duas vezes o tempo em que dois trechos se sobrepõem.
         */
        void acrescentar(Leitura leitura) {
            LocalDateTime comeco = leitura.getDataLeitura();
            LocalDateTime termino = comeco.plusSeconds(leitura.getDuracaoFluxoSeg());
            long sobreposicao = fim != null && fim.isAfter(comeco) ? Duration.between(comeco, fim).getSeconds() : 0;
            duracaoSeg += Math.max(0, leitura.getDuracaoFluxoSeg() - sobreposicao);
            volume += leitura.getVolumeLitros();
            if (inicio == null) {
                inicio = comeco;
            }
            if (fim == null || termino.isAfter(fim)) {
                fim = termino;
            }
            leituras.add(leitura);
        }

        double minutos() {
            return duracaoSeg / 60.0;
        }

        /**
         * Segundos do fluxo que caem no período de menor consumo da residência, sem contar a
         * sobreposição de
         * trechos.
         */
        long segundosNoPeriodoMenorConsumo(Residencia residencia) {
            long total = 0;
            LocalDateTime ate = null;
            for (Leitura leitura : leituras) {
                LocalDateTime comeco = leitura.getDataLeitura();
                LocalDateTime termino = comeco.plusSeconds(leitura.getDuracaoFluxoSeg());
                if (ate != null && ate.isAfter(comeco)) {
                    comeco = ate;
                }
                if (termino.isAfter(comeco)) {
                    total += residencia.segundosNoPeriodoMenorConsumo(comeco, termino);
                    ate = termino;
                }
            }
            return Math.min(total, duracaoSeg);
        }

        double vazaoMedia() {
            return duracaoSeg > 0 ? volume / minutos() : 0;
        }
    }

    /**
     * Anomalia encontrada pelas regras, ainda sem alerta: na conciliação ela vira alerta ou se
     * junta a um
     * que já existe.
     */
    static class Ocorrencia {
        TipoAlertaEnum tipo;
        TipoGravidadeEnum gravidade;
        LocalDateTime inicio;
        LocalDateTime fim;
        int idDispositivo;
        Double volume;
        String motivo;
        String situacao;
        Integer idAlerta;
    }

    private final AlertaDAO alertaDAO;
    private final LeituraDAO leituraDAO;
    private final PerfilConsumoDAO perfilDAO;
    private final ResidenciaDAO residenciaDAO;
    private final DispositivoDAO dispositivoDAO;
    private final NotificacaoService notificacaoService;

    public AnaliseConsumoService() {
        this.alertaDAO = new AlertaDAO();
        this.leituraDAO = new LeituraDAO();
        this.perfilDAO = new PerfilConsumoDAO();
        this.residenciaDAO = new ResidenciaDAO();
        this.dispositivoDAO = new DispositivoDAO();
        this.notificacaoService = new NotificacaoService();
    }

    // ------------------------------------------------------------------ operações

    /**
     * Roda a análise nas residências em que o morador é titular (as outras são ignoradas) e grava
     * os alertas das anomalias novas.
     *
     * @throws IllegalArgumentException se o morador não é titular de nenhuma residência
     */
    public List<AnaliseResultadoResponseDTO> executarPorMorador(int idMorador) {
        List<AnaliseResultadoResponseDTO> resultados = new ArrayList<>();
        for (ResidenciaDAO.ResidenciaComVinculo v : residenciaDAO.listarPorMorador(idMorador)) {
            if (v.tipoVinculo() == br.com.fiap.flux.enums.TipoVinculoEnum.TITULAR) {
                resultados.add(analisar(v.residencia(), true));
            }
        }
        if (resultados.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência titular encontrada para o morador informado");
        }
        return resultados;
    }

    /**
     * Resultado atual de cada residência do morador: refaz o cálculo com as leituras de agora, sem
     * gravar nada, e mostra quais anomalias já têm alerta ({@code NOVO} para as que ainda não têm).
     *
     * @throws IllegalArgumentException se o morador não tem nenhuma residência
     */
    public List<AnaliseResultadoResponseDTO> resultadoPorMorador(int idMorador) {
        List<AnaliseResultadoResponseDTO> resultados = new ArrayList<>();
        for (ResidenciaDAO.ResidenciaComVinculo v : listarResidencias(idMorador)) {
            resultados.add(analisar(v.residencia(), false));
        }
        return resultados;
    }

    /**
     * Histórico de alertas de todas as residências do morador, juntos e do mais recente para o mais
     * antigo. {@code inicio} e {@code fim} (opcionais) filtram pela data de identificação:
     * "2026-11-01" ou "2026-11-01T08:00".
     *
     * @throws IllegalArgumentException se o período é inválido ou o morador não tem residência
     */
    public List<AnaliseHistoricoResponseDTO> historicoPorMorador(int idMorador, String inicio, String fim) {
        LocalDateTime de = ConsumoUtil.lerPeriodo(inicio, "inicio", false);
        LocalDateTime ate = ConsumoUtil.lerPeriodo(fim, "fim", true);
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("O início do período não pode ser depois do fim.");
        }

        List<AnaliseHistoricoResponseDTO> historico = new ArrayList<>();
        for (ResidenciaDAO.ResidenciaComVinculo v : listarResidencias(idMorador)) {
            for (Alerta alerta : alertaDAO.listarPorResidencia(v.residencia().getIdResidencia(), de, ate)) {
                historico.add(AnaliseMapper.toHistoricoDto(alerta));
            }
        }
        historico.sort(java.util.Comparator.comparing(AnaliseHistoricoResponseDTO::dataIdentificacao).reversed());
        return historico;
    }

    /**
     * Roda a análise sozinha a cada 5 minutos (RN01 e RN02), em todas as residências com
     * dispositivo. Uma
     * falha não derruba o agendamento: tenta de novo no próximo ciclo.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void executarAgendado() {
        try {
            executarTodas();
        } catch (RuntimeException e) {
            System.out.println("AVISO: a análise automática falhou: " + e.getMessage());
        }
    }

    /**
     * Análise automática de uma residência que acabou de receber leituras (RN01 e RN02). Não
     * confere
     * acesso, porque quem chama já validou, e grava os alertas como a execução manual.
     */
    public AnaliseResultadoResponseDTO analisarAposNovasLeituras(int idResidencia) {
        return analisar(exigirResidencia(idResidencia), true);
    }

    /**
     * Roda a análise em todas as residências que têm dispositivo. É uma operação administrativa, de
     * demonstração. Uma falha numa residência não interrompe as demais: ela aparece no resumo com a
     * mensagem de erro.
     */
    public AnaliseGeralResponseDTO executarTodas() {
        Set<Integer> ids = new TreeSet<>();
        for (Dispositivo dispositivo : dispositivoDAO.listar()) {
            ids.add(dispositivo.getResidencia().getIdResidencia());
        }

        List<AnaliseGeralResponseDTO.Residencia> itens = new ArrayList<>();
        int falhas = 0;
        int criados = 0;
        int atualizados = 0;
        int existentes = 0;
        int resolvidos = 0;
        for (int idResidencia : ids) {
            try {
                Residencia residencia = exigirResidencia(idResidencia);
                AnaliseResultadoResponseDTO r = analisar(residencia, true);
                itens.add(new AnaliseGeralResponseDTO.Residencia(idResidencia, r.leiturasAnalisadas(),
                        r.anomalias().size(), r.alertasCriados(), r.alertasAtualizados(),
                        r.alertasJaExistentes(), r.alertasResolvidos(), null));
                criados += r.alertasCriados();
                atualizados += r.alertasAtualizados();
                existentes += r.alertasJaExistentes();
                resolvidos += r.alertasResolvidos();
            } catch (RuntimeException e) {
                falhas++;
                System.out.println("AVISO: a análise da residência " + idResidencia + " falhou: " + e.getMessage());
                itens.add(new AnaliseGeralResponseDTO.Residencia(idResidencia, 0, 0, 0, 0, 0, 0,
                        e.getMessage() != null ? e.getMessage() : "Falha inesperada na análise."));
            }
        }
        return new AnaliseGeralResponseDTO(LocalDateTime.now(), ids.size() - falhas, falhas,
                criados, atualizados, existentes, resolvidos, itens);
    }

    // ------------------------------------------------------------------ análise

    /**
     * Carrega os dados, detecta as anomalias e concilia com os alertas existentes. Com {@code
     * gravar},
     * também cria/atualiza alertas, resolve os antigos (RN09) e confere os sensores (RN13); sem
     * ele, só
     * calcula e mostra.
     */
    private AnaliseResultadoResponseDTO analisar(Residencia residencia, boolean gravar) {
        int idResidencia = residencia.getIdResidencia();
        List<Leitura> leituras = leituraDAO.listarFiltrado(idResidencia, null, null, null, null);
        PerfilConsumo perfil = perfilDAO.buscarPorResidencia(idResidencia);
        List<String> observacoes = new ArrayList<>();
        boolean perfilConfiavel = perfil != null && perfil.possuiHistoricoSuficiente();

        if (leituras.isEmpty()) {
            observacoes.add("A residência ainda não tem leituras: não há o que analisar.");
            return montarResultado(idResidencia, null, null, 0, perfilConfiavel, List.of(), 0, observacoes);
        }

        LocalDateTime fimJanela = leituras.get(0).getDataLeitura();
        for (Leitura leitura : leituras) {
            LocalDateTime termino = leitura.getDataLeitura().plusSeconds(leitura.getDuracaoFluxoSeg());
            if (termino.isAfter(fimJanela)) {
                fimJanela = termino;
            }
        }
        LocalDateTime inicioJanela = fimJanela.toLocalDate().minusDays(JANELA_DIAS - 1L).atStartOfDay();
        long naJanela = leituras.stream().filter(l -> !l.getDataLeitura().isBefore(inicioJanela)).count();

        if (perfil == null) {
            observacoes.add("Perfil de consumo ainda não calculado: as comparações com o padrão (vazão, "
                    + "horário de menor consumo e variação diária) não foram feitas. Use "
                    + "POST /residencias/" + idResidencia + "/perfil-consumo/calcular.");
        } else if (!perfilConfiavel) {
            observacoes.add("Perfil de consumo com histórico insuficiente: as comparações com o padrão "
                    + "(vazão, horário de menor consumo e variação diária) não foram feitas.");
        }

        List<Ocorrencia> ocorrencias = detectar(residencia, perfil, leituras, inicioJanela);
        conciliar(residencia, ocorrencias, gravar);

        int resolvidos = 0;
        if (gravar) {
            resolvidos = resolverAutomaticamente(leituras, ocorrencias, idResidencia);
            verificarSensores(residencia, leituras, fimJanela, observacoes);
        }
        return montarResultado(idResidencia, inicioJanela, fimJanela, (int) naJanela, perfilConfiavel,
                ocorrencias, resolvidos, observacoes);
    }

    /**
     * Aplica as regras de análise, sem acessar o banco. As {@code leituras} vêm em ordem
     * cronológica; só
     * entram os fluxos que terminam na janela e os dias da janela. Devolve as ocorrências da mais
     * recente
     * para a mais antiga.
     */
    List<Ocorrencia> detectar(Residencia residencia, PerfilConsumo perfil, List<Leitura> leituras,
                              LocalDateTime inicioJanela) {
        List<Periodo> ausencias = ConsumoUtil.lerAusencias(residencia.getPeriodoAusencia());
        boolean perfilConfiavel = perfil != null && perfil.possuiHistoricoSuficiente();
        double consumoMedio = perfilConfiavel ? perfil.getConsumoMedio() : 0;
        double vazaoMedia = perfilConfiavel ? perfil.getVazaoMedia() : 0;

        List<Ocorrencia> ocorrencias = new ArrayList<>();
        Set<Leitura> usadas = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Fluxo> fluxos = montarFluxos(leituras).stream().filter(f -> !f.fim.isBefore(inicioJanela)).toList();

        // 1. Duração do fluxo contra os limites da residência (só a regra de maior gravidade vale).
        for (Fluxo fluxo : fluxos) {
            Ocorrencia ocorrencia = classificarPelaDuracao(fluxo, residencia, ausencias, vazaoMedia);
            if (ocorrencia != null) {
                ocorrencias.add(ocorrencia);
                usadas.addAll(fluxo.leituras);
            }
        }

        // Consumo da janela que ainda não foi explicado por vazamento/ponto aberto.
        List<Leitura> restantes = leituras.stream()
                .filter(l -> !l.getDataLeitura().isBefore(inicioJanela) && l.getVolumeLitros() > 0)
                .filter(l -> !usadas.contains(l))
                .toList();

        // 2. Consumo em período de ausência, dia a dia.
        for (List<Leitura> dia : agruparPor(restantes, l -> ConsumoUtil.emAusencia(ausencias, l.getDataLeitura().toLocalDate())
                ? l.getDataLeitura().toLocalDate() : null)) {
            Ocorrencia ocorrencia = consumoEmAusencia(dia, consumoMedio);
            if (ocorrencia != null) {
                ocorrencias.add(ocorrencia);
                usadas.addAll(dia);
            }
        }
        restantes = restantes.stream().filter(l -> !usadas.contains(l)).toList();

        // 3. Consumo no horário de menor utilização, noite a noite, em conjunto com o perfil.
        if (consumoMedio > 0) {
            LocalDate primeiroDia = inicioJanela.toLocalDate();
            for (List<Leitura> noite : agruparPor(restantes, l -> {
                LocalDate dia = diaDaJanelaDeMenorConsumo(residencia, l.getDataLeitura());
                // noite que começou antes da janela fica de fora inteira: a chave não pode mudar entre execuções
                return dia != null && !dia.isBefore(primeiroDia) ? dia : null;
            })) {
                Ocorrencia ocorrencia = consumoNoHorarioDeMenorUso(noite, consumoMedio);
                if (ocorrencia != null) {
                    ocorrencias.add(ocorrencia);
                    usadas.addAll(noite);
                }
            }
            restantes = restantes.stream().filter(l -> !usadas.contains(l)).toList();
        }

        // 4. Vazão do fluxo contra a vazão média do perfil.
        if (vazaoMedia > 0) {
            for (Fluxo fluxo : fluxos) {
                boolean livre = fluxo.leituras.stream().noneMatch(usadas::contains);
                if (livre) {
                    Ocorrencia ocorrencia = vazaoAcimaDoPadrao(fluxo, vazaoMedia);
                    if (ocorrencia != null) {
                        ocorrencias.add(ocorrencia);
                        usadas.addAll(fluxo.leituras);
                    }
                }
            }
            restantes = restantes.stream().filter(l -> !usadas.contains(l)).toList();
        }

        // 5. Variação significativa do consumo diário.
        if (consumoMedio > 0) {
            for (List<Leitura> dia : agruparPor(restantes, l -> l.getDataLeitura().toLocalDate())) {
                Ocorrencia ocorrencia = variacaoDiaria(dia, perfil);
                if (ocorrencia != null) {
                    ocorrencias.add(ocorrencia);
                }
            }
        }

        ocorrencias.sort(Comparator.comparing((Ocorrencia o) -> o.inicio).reversed());
        return ocorrencias;
    }

    // ------------------------------------------------------------------ regras

    /**
     * Regra 1 (RN04 e RN05): duração do fluxo contra os limites, conforme o período de menor
     * consumo.
     * Vazamento: mais que o limite de vazamento <i>dentro</i> do período de menor consumo. Ponto
     * aberto:
     * mais que o limite de ponto aberto <i>fora</i> dele. Vazamento prevalece sobre ponto aberto.
     */
    private Ocorrencia classificarPelaDuracao(Fluxo fluxo, Residencia residencia, List<Periodo> ausencias,
                                              double vazaoMedia) {
        long limiteVazamentoSeg = residencia.getLimiteVazamentoMin() * 60L;
        long limitePontoAbertoSeg = residencia.getLimitePontoAberto() * 60L;
        long dentroSeg = fluxo.segundosNoPeriodoMenorConsumo(residencia);
        long foraSeg = fluxo.duracaoSeg - dentroSeg;
        boolean vazamento = dentroSeg > limiteVazamentoSeg;
        boolean pontoAberto = !vazamento && foraSeg > limitePontoAbertoSeg;
        if (!vazamento && !pontoAberto) {
            return null;
        }

        boolean ausente = ConsumoUtil.emAusencia(ausencias, fluxo.inicio.toLocalDate());
        boolean vazaoAlta = vazaoMedia > 0 && fluxo.vazaoMedia() >= FATOR_VAZAO_ALTA * vazaoMedia;

        Ocorrencia ocorrencia = nova(fluxo);
        List<String> detalhes = new ArrayList<>();
        if (vazamento) {
            ocorrencia.tipo = TipoAlertaEnum.VAZAMENTO_COMUM;
            ocorrencia.gravidade = TipoGravidadeEnum.CRITICO;
            ocorrencia.volume = ConsumoUtil.arredondar(fluxo.volume);
            ocorrencia.motivo = String.format(PT_BR,
                    "Fluxo contínuo de %.0f min, %.0f min deles no período de menor consumo (%s às %s), "
                            + "acima do limite de vazamento (%d min)",
                    fluxo.minutos(), dentroSeg / 60.0,
                    residencia.inicioMenorConsumoEfetivo(), residencia.fimMenorConsumoEfetivo(),
                    residencia.getLimiteVazamentoMin());
        } else {
            ocorrencia.tipo = TipoAlertaEnum.PONTO_ABERTO;
            ocorrencia.gravidade = ausente || vazaoAlta ? TipoGravidadeEnum.CRITICO : TipoGravidadeEnum.ATENCAO;
            // o que passou do limite é desperdício; em ausência ninguém estava usando, então é tudo
            double excedente = ausente ? 1.0 : (foraSeg - limitePontoAbertoSeg) / (double) fluxo.duracaoSeg;
            ocorrencia.volume = ConsumoUtil.arredondar(fluxo.volume * excedente);
            ocorrencia.motivo = String.format(PT_BR,
                    "Fluxo contínuo de %.0f min, %.0f min deles fora do período de menor consumo, "
                            + "acima do limite de ponto aberto (%d min)",
                    fluxo.minutos(), foraSeg / 60.0, residencia.getLimitePontoAberto());
        }
        if (ausente) {
            detalhes.add("durante período de ausência");
        }
        if (vazaoAlta) {
            detalhes.add(String.format(PT_BR, "vazão de %.1f L/min, muito acima do padrão (%.1f L/min)",
                    fluxo.vazaoMedia(), vazaoMedia));
        }
        if (!detalhes.isEmpty()) {
            ocorrencia.motivo += "; " + String.join("; ", detalhes);
        }
        return ocorrencia;
    }

    /**
     * Regra 2: consumo em um dia de ausência, já sem o que foi explicado por vazamento ou ponto
     * aberto.
     */
    private Ocorrencia consumoEmAusencia(List<Leitura> dia, double consumoMedio) {
        double volume = somarVolume(dia);
        if (volume < VOLUME_MINIMO_LITROS) {
            return null;
        }
        Leitura primeira = dia.get(0);
        Ocorrencia ocorrencia = nova(dia, primeira.getDispositivo().getIdDispositivo());
        ocorrencia.tipo = TipoAlertaEnum.FORA_DO_PADRAO;
        ocorrencia.gravidade = consumoMedio > 0 && volume >= consumoMedio * FRACAO_AUSENCIA_CRITICA
                ? TipoGravidadeEnum.CRITICO : TipoGravidadeEnum.ATENCAO;
        ocorrencia.volume = ConsumoUtil.arredondar(volume);
        ocorrencia.motivo = String.format(PT_BR, "Consumo de %.1f L em período de ausência da residência", volume);
        return ocorrencia;
    }

    /**
     * Regra 3: consumo, numa noite, no horário de menor utilização, comparado ao consumo médio
     * diário.
     */
    private Ocorrencia consumoNoHorarioDeMenorUso(List<Leitura> noite, double consumoMedio) {
        double volume = somarVolume(noite);
        double fracao = volume / consumoMedio;
        if (volume < VOLUME_MINIMO_LITROS || fracao < FRACAO_NOITE_ATENCAO) {
            return null;
        }
        Ocorrencia ocorrencia = nova(noite, noite.get(0).getDispositivo().getIdDispositivo());
        ocorrencia.tipo = TipoAlertaEnum.FORA_DO_PADRAO;
        ocorrencia.gravidade = fracao >= FRACAO_NOITE_CRITICA ? TipoGravidadeEnum.CRITICO : TipoGravidadeEnum.ATENCAO;
        ocorrencia.volume = null; // consumo de madrugada não é, por si só, desperdício comprovado
        ocorrencia.motivo = String.format(PT_BR,
                "Consumo de %.1f L no horário de menor utilização (%.0f%% do consumo médio diário de %.0f L)",
                volume, fracao * 100, consumoMedio);
        return ocorrencia;
    }

    /** Regra 4: fluxo cuja vazão média está muito acima da vazão média do perfil. */
    private Ocorrencia vazaoAcimaDoPadrao(Fluxo fluxo, double vazaoMedia) {
        double fator = fluxo.vazaoMedia() / vazaoMedia;
        if (fluxo.duracaoSeg < DURACAO_MINIMA_VAZAO_SEG || fator < FATOR_VAZAO_ALTA) {
            return null;
        }
        Ocorrencia ocorrencia = nova(fluxo);
        ocorrencia.tipo = TipoAlertaEnum.FORA_DO_PADRAO;
        ocorrencia.gravidade = fator >= FATOR_VAZAO_CRITICA ? TipoGravidadeEnum.CRITICO : TipoGravidadeEnum.ATENCAO;
        // desperdício estimado: o que passou do volume que a vazão habitual daria no mesmo tempo
        ocorrencia.volume = ConsumoUtil.arredondar(Math.max(0, fluxo.volume - vazaoMedia * fluxo.minutos()));
        ocorrencia.motivo = String.format(PT_BR,
                "Vazão de %.1f L/min por %.0f min, %.1f vez(es) a vazão média da residência (%.1f L/min)",
                fluxo.vazaoMedia(), fluxo.minutos(), fator, vazaoMedia);
        return ocorrencia;
    }

    /**
     * Regra 5: total do dia muito acima do consumo médio (precisa passar os desvios padrão e o
     * múltiplo).
     * O dispositivo que mais consumiu no dia é o que leva o alerta.
     */
    private Ocorrencia variacaoDiaria(List<Leitura> dia, PerfilConsumo perfil) {
        double total = somarVolume(dia);
        if (perfil.estaNoPadrao(total)) {
            return null;
        }
        double media = perfil.getConsumoMedio();
        double desvio = perfil.getDesvioPadrao();
        boolean critico = total >= media + DESVIOS_CRITICO * desvio && total >= media * MULTIPLO_CRITICO;
        // o dispositivo com mais consumo no dia é o que leva o alerta
        Map<Integer, Double> porDispositivo = new TreeMap<>();
        for (Leitura leitura : dia) {
            porDispositivo.merge(leitura.getDispositivo().getIdDispositivo(), leitura.getVolumeLitros(), Double::sum);
        }
        int idDispositivo = Collections.max(porDispositivo.entrySet(), Map.Entry.comparingByValue()).getKey();

        Ocorrencia ocorrencia = nova(dia, idDispositivo);
        ocorrencia.inicio = dia.get(0).getDataLeitura().toLocalDate().atStartOfDay(); // chave: o dia analisado
        ocorrencia.tipo = TipoAlertaEnum.FORA_DO_PADRAO;
        ocorrencia.gravidade = critico ? TipoGravidadeEnum.CRITICO : TipoGravidadeEnum.ATENCAO;
        ocorrencia.volume = ConsumoUtil.arredondar(Math.max(0, total - media));
        ocorrencia.motivo = String.format(PT_BR,
                "Consumo de %.0f L no dia, %.1f vez(es) o consumo médio diário (%.0f L)", total, total / media, media);
        return ocorrencia;
    }

    // ------------------------------------------------------------------ fluxos e janelas

    /**
     * Junta os trechos de cada dispositivo em fluxos contínuos. Trecho sem duração ou sem consumo
     * não é
     * fluxo.
     */
    private List<Fluxo> montarFluxos(List<Leitura> leituras) {
        Map<Integer, List<Leitura>> porDispositivo = new TreeMap<>();
        for (Leitura leitura : leituras) {
            if (leitura.getDuracaoFluxoSeg() > 0 && (leitura.getVazaoLitroMinuto() > 0 || leitura.getVolumeLitros() > 0)) {
                porDispositivo.computeIfAbsent(leitura.getDispositivo().getIdDispositivo(), id -> new ArrayList<>())
                        .add(leitura);
            }
        }

        List<Fluxo> fluxos = new ArrayList<>();
        for (Map.Entry<Integer, List<Leitura>> entrada : porDispositivo.entrySet()) {
            List<Leitura> trechos = entrada.getValue();
            trechos.sort(Comparator.comparing(Leitura::getDataLeitura).thenComparingInt(Leitura::getSequencia));
            Fluxo atual = null;
            for (Leitura trecho : trechos) {
                boolean continua = atual != null
                        && !trecho.getDataLeitura().isAfter(atual.fim.plusSeconds(TOLERANCIA_LACUNA_SEG));
                if (!continua) {
                    atual = new Fluxo(entrada.getKey());
                    fluxos.add(atual);
                }
                atual.acrescentar(trecho);
            }
        }
        return fluxos;
    }

    /**
     * Dia em que começou a janela de menor consumo à qual o horário pertence, ou {@code null} se o
     * horário está fora dela. Com janela que vira a meia-noite (ex.: 22h às 6h), a madrugada
     * pertence ao
     * dia anterior.
     */
    private LocalDate diaDaJanelaDeMenorConsumo(Residencia residencia, LocalDateTime momento) {
        LocalTime hora = momento.toLocalTime();
        if (!residencia.estaNoPeriodoMenorConsumo(hora)) {
            return null;
        }
        boolean viraMeiaNoite = !residencia.inicioMenorConsumoEfetivo().isBefore(residencia.fimMenorConsumoEfetivo());
        return viraMeiaNoite && hora.isBefore(residencia.inicioMenorConsumoEfetivo())
                ? momento.toLocalDate().minusDays(1) : momento.toLocalDate();
    }

    /**
     * Agrupa as leituras pela chave (em ordem de chave); chave {@code null} deixa a leitura de
     * fora.
     */
    private <K extends Comparable<? super K>> List<List<Leitura>> agruparPor(List<Leitura> leituras,
                                                                             java.util.function.Function<Leitura, K> chave) {
        Map<K, List<Leitura>> grupos = new TreeMap<>();
        for (Leitura leitura : leituras) {
            K k = chave.apply(leitura);
            if (k != null) {
                grupos.computeIfAbsent(k, x -> new ArrayList<>()).add(leitura);
            }
        }
        return new ArrayList<>(grupos.values());
    }

    // ------------------------------------------------------------------ alertas

    /**
     * Confere cada ocorrência com os alertas da residência e, com {@code gravar}, cria os que
     * faltam e
     * atualiza os que pioraram. Sem {@code gravar}, só marca a situação ({@code JA_EXISTENTE} ou
     * {@code NOVO}).
     */
    private void conciliar(Residencia residencia, List<Ocorrencia> ocorrencias, boolean gravar) {
        if (ocorrencias.isEmpty()) {
            return;
        }
        synchronized (TRAVA_GRAVACAO) {
            List<Alerta> existentes = new ArrayList<>(alertaDAO.listarPorResidencia(residencia.getIdResidencia(), null, null));
            for (Ocorrencia ocorrencia : ocorrencias) {
                Alerta existente = encontrar(existentes, ocorrencia);
                if (existente == null) {
                    if (!gravar) {
                        ocorrencia.situacao = SITUACAO_NOVO;
                        continue;
                    }
                    Alerta novo = montarAlerta(residencia, ocorrencia);
                    alertaDAO.cadastrar(novo);
                    if (novo.getIdAlerta() == 0) {
                        throw new IllegalStateException("Não foi possível gravar o alerta.");
                    }
                    existentes.add(novo);
                    notificar(novo);
                    ocorrencia.idAlerta = novo.getIdAlerta();
                    ocorrencia.situacao = SITUACAO_CRIADO;
                    continue;
                }

                ocorrencia.idAlerta = existente.getIdAlerta();
                ocorrencia.situacao = SITUACAO_JA_EXISTENTE;
                if (gravar && existente.getStatusAlerta() != StatusAlertaEnum.RESOLVIDO && piorou(existente, ocorrencia)) {
                    atualizar(existente, ocorrencia);
                    alertaDAO.alterar(existente);
                    ocorrencia.situacao = SITUACAO_ATUALIZADO;
                }
                // o resultado mostra o alerta como está, não só o que a análise calculou agora
                ocorrencia.tipo = existente.getTipoAlerta();
                ocorrencia.gravidade = existente.getTipoGravidade();
                ocorrencia.volume = existente.getVolumeDesperdicado();
            }
        }
    }

    /**
     * Procura o alerta da ocorrência. O identificador lógico é o mesmo dispositivo, a mesma classe
     * e o
     * mesmo momento de início; ponto aberto e vazamento são uma só classe (um fluxo tem um único
     * alerta,
     * que pode escalar).
     */
    private Alerta encontrar(List<Alerta> existentes, Ocorrencia ocorrencia) {
        LocalDateTime inicio = ocorrencia.inicio.truncatedTo(ChronoUnit.SECONDS);
        for (Alerta alerta : existentes) {
            if (alerta.getDispositivo().getIdDispositivo() == ocorrencia.idDispositivo
                    && ehDeFluxo(alerta.getTipoAlerta()) == ehDeFluxo(ocorrencia.tipo)
                    && alerta.getDataIdentificacao().truncatedTo(ChronoUnit.SECONDS).equals(inicio)) {
                return alerta;
            }
        }
        return null;
    }

    /** Ponto aberto e vazamento nascem de um fluxo contínuo; fora do padrão, não. */
    private boolean ehDeFluxo(TipoAlertaEnum tipo) {
        return tipo != TipoAlertaEnum.FORA_DO_PADRAO;
    }

    /**
     * Diz se a ocorrência piorou em relação ao alerta: virou vazamento, ficou crítica ou
     * desperdiçou mais.
     */
    private boolean piorou(Alerta existente, Ocorrencia ocorrencia) {
        boolean virouVazamento = ocorrencia.tipo == TipoAlertaEnum.VAZAMENTO_COMUM
                && existente.getTipoAlerta() == TipoAlertaEnum.PONTO_ABERTO;
        boolean ficouCritica = ocorrencia.gravidade == TipoGravidadeEnum.CRITICO
                && existente.getTipoGravidade() == TipoGravidadeEnum.ATENCAO;
        boolean desperdicouMais = ocorrencia.volume != null
                && (existente.getVolumeDesperdicado() == null || ocorrencia.volume > existente.getVolumeDesperdicado());
        return virouVazamento || ficouCritica || desperdicouMais;
    }

    /**
     * Aplica a piora ao alerta. Só sobe: nunca troca vazamento por ponto aberto, crítico por
     * atenção nem
     * diminui o volume.
     */
    private void atualizar(Alerta existente, Ocorrencia ocorrencia) {
        if (ocorrencia.tipo == TipoAlertaEnum.VAZAMENTO_COMUM && existente.getTipoAlerta() == TipoAlertaEnum.PONTO_ABERTO) {
            existente.setTipoAlerta(TipoAlertaEnum.VAZAMENTO_COMUM);
        }
        if (ocorrencia.gravidade == TipoGravidadeEnum.CRITICO) {
            existente.setTipoGravidade(TipoGravidadeEnum.CRITICO);
        }
        if (ocorrencia.volume != null
                && (existente.getVolumeDesperdicado() == null || ocorrencia.volume > existente.getVolumeDesperdicado())) {
            existente.setVolumeDesperdicado(ocorrencia.volume);
        }
    }

    /**
     * Cria as notificações do alerta novo (moradores e contatos com autorização ativa). O alerta já
     * foi
     * gravado: se as notificações falharem, ele continua valendo e o erro só vai para o log.
     */
    private void notificar(Alerta alerta) {
        try {
            notificacaoService.gerarParaAlerta(alerta);
        } catch (RuntimeException e) {
            System.out.println("AVISO: não foi possível gerar as notificações do alerta "
                    + alerta.getIdAlerta() + ": " + e.getMessage());
        }
    }

    /** Alerta novo: ABERTO e sem morador responsável, porque foi gerado pela análise. */
    private Alerta montarAlerta(Residencia residencia, Ocorrencia ocorrencia) {
        Alerta alerta = new Alerta();
        alerta.setTipoAlerta(ocorrencia.tipo);
        alerta.setTipoGravidade(ocorrencia.gravidade);
        alerta.setStatusAlerta(StatusAlertaEnum.ABERTO);
        alerta.setDataIdentificacao(ocorrencia.inicio.truncatedTo(ChronoUnit.SECONDS));
        alerta.setVolumeDesperdicado(ocorrencia.volume);
        Residencia referencia = new Residencia();
        referencia.setIdResidencia(residencia.getIdResidencia());
        alerta.setResidencia(referencia);
        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setIdDispositivo(ocorrencia.idDispositivo);
        alerta.setDispositivo(dispositivo);
        return alerta;
    }

    // ------------------------------------------------------------------ resolução automática e sensor

    /**
     * RN09: fecha os alertas em aberto cujo dispositivo ficou 48 h seguidas sem nova anomalia. O
     * relógio
     * das 48 h parte do fim da última anomalia do dispositivo (a do próprio alerta ou qualquer
     * outra
     * encontrada agora) e vai até a última leitura do dispositivo.
     *
     * @return quantos alertas foram resolvidos
     */
    private int resolverAutomaticamente(List<Leitura> leituras, List<Ocorrencia> ocorrencias, int idResidencia) {
        Map<Integer, LocalDateTime> ultimaLeitura = new TreeMap<>();
        for (Leitura leitura : leituras) {
            ultimaLeitura.merge(leitura.getDispositivo().getIdDispositivo(),
                    leitura.getDataLeitura().plusSeconds(leitura.getDuracaoFluxoSeg()),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        List<Fluxo> fluxos = montarFluxos(leituras);
        Duration necessario = Duration.ofHours(HORAS_FLUXO_NORMAL_PARA_RESOLVER);
        int resolvidos = 0;

        synchronized (TRAVA_GRAVACAO) {
            for (Alerta alerta : alertaDAO.listarPorResidencia(idResidencia, null, null)) {
                if (alerta.getStatusAlerta() == StatusAlertaEnum.RESOLVIDO || alerta.getDispositivo() == null) {
                    continue;
                }
                int idDispositivo = alerta.getDispositivo().getIdDispositivo();
                LocalDateTime referencia = ultimaLeitura.get(idDispositivo);
                if (referencia == null) {
                    continue;
                }

                LocalDateTime identificacao = alerta.getDataIdentificacao().truncatedTo(ChronoUnit.SECONDS);
                LocalDateTime ultimaAnomalia = identificacao;
                if (ehDeFluxo(alerta.getTipoAlerta())) {
                    for (Fluxo fluxo : fluxos) {
                        if (fluxo.idDispositivo == idDispositivo
                                && fluxo.inicio.truncatedTo(ChronoUnit.SECONDS).equals(identificacao)
                                && fluxo.fim.isAfter(ultimaAnomalia)) {
                            ultimaAnomalia = fluxo.fim;
                        }
                    }
                }
                for (Ocorrencia ocorrencia : ocorrencias) {
                    if (ocorrencia.idDispositivo == idDispositivo
                            && !ocorrencia.fim.isBefore(identificacao)
                            && ocorrencia.fim.isAfter(ultimaAnomalia)) {
                        ultimaAnomalia = ocorrencia.fim;
                    }
                }

                if (Duration.between(ultimaAnomalia, referencia).compareTo(necessario) >= 0) {
                    alerta.resolverAutomaticamente(ultimaAnomalia.plusHours(HORAS_FLUXO_NORMAL_PARA_RESOLVER));
                    alertaDAO.atualizarStatus(alerta);
                    resolvidos++;
                }
            }
        }
        return resolvidos;
    }

    /**
     * RN13: marca o sensor como em possível falha (ou de volta ao normal) conforme a passagem de
     * água.
     * {@code referencia} é a última leitura da residência. Só grava quando o estado muda, e cada
     * mudança
     * vira uma observação no resultado.
     */
    private void verificarSensores(Residencia residencia, List<Leitura> leituras, LocalDateTime referencia,
                                   List<String> observacoes) {
        List<Periodo> ausencias = ConsumoUtil.lerAusencias(residencia.getPeriodoAusencia());
        Duration limite = Duration.ofHours(HORAS_SEM_PASSAGEM_SENSOR);

        for (Dispositivo dispositivo : dispositivoDAO.listarPorResidencia(residencia.getIdResidencia())) {
            LocalDateTime primeiraLeitura = null;
            LocalDateTime ultimaPassagem = null;
            for (Leitura leitura : leituras) {
                if (leitura.getDispositivo().getIdDispositivo() != dispositivo.getIdDispositivo()) {
                    continue;
                }
                if (primeiraLeitura == null || leitura.getDataLeitura().isBefore(primeiraLeitura)) {
                    primeiraLeitura = leitura.getDataLeitura();
                }
                if (leitura.getVolumeLitros() > 0 || leitura.getVazaoLitroMinuto() > 0) {
                    LocalDateTime fim = leitura.getDataLeitura().plusSeconds(leitura.getDuracaoFluxoSeg());
                    if (ultimaPassagem == null || fim.isAfter(ultimaPassagem)) {
                        ultimaPassagem = fim;
                    }
                }
            }
            if (primeiraLeitura == null) {
                continue; // dispositivo sem nenhuma leitura: não há como avaliar
            }

            // a contagem de "sem passagem" só vale a partir do fim da última ausência
            LocalDateTime habitadaSemAguaDesde = ultimaPassagem != null ? ultimaPassagem : primeiraLeitura;
            for (Periodo ausencia : ausencias) {
                LocalDateTime fimAusencia = ausencia.fim().plusDays(1).atStartOfDay();
                if (ausencia.inicio().atStartOfDay().isBefore(referencia) && fimAusencia.isAfter(habitadaSemAguaDesde)) {
                    habitadaSemAguaDesde = fimAusencia;
                }
            }

            boolean semPassagem = Duration.between(habitadaSemAguaDesde, referencia).compareTo(limite) >= 0;
            boolean passouRecentemente = ultimaPassagem != null
                    && Duration.between(ultimaPassagem, referencia).compareTo(limite) < 0;

            if (semPassagem && dispositivo.isSensorOk()) {
                dispositivo.setSensorOk(false);
                dispositivoDAO.alterarStatus(dispositivo);
                observacoes.add(String.format(PT_BR,
                        "Dispositivo %d: sensor em possível falha (sem passagem de água há %d h em residência habitada).",
                        dispositivo.getIdDispositivo(),
                        Duration.between(habitadaSemAguaDesde, referencia).toHours()));
            } else if (!semPassagem && passouRecentemente && !dispositivo.isSensorOk()) {
                dispositivo.setSensorOk(true);
                dispositivoDAO.alterarStatus(dispositivo);
                observacoes.add("Dispositivo " + dispositivo.getIdDispositivo()
                        + ": a água voltou a passar, sensor de volta ao normal.");
            }
        }
    }

    /**
     * Monta a resposta: conta os alertas criados, atualizados e já existentes pela situação de cada
     * ocorrência e acrescenta a observação de "nenhuma anomalia" quando for o caso.
     */
    private AnaliseResultadoResponseDTO montarResultado(int idResidencia, LocalDateTime inicioJanela,
                                                        LocalDateTime fimJanela, int leiturasAnalisadas,
                                                        boolean perfilConfiavel, List<Ocorrencia> ocorrencias,
                                                        int resolvidos, List<String> observacoes) {
        int criados = 0;
        int atualizados = 0;
        int existentes = 0;
        List<AnaliseResultadoResponseDTO.Anomalia> anomalias = new ArrayList<>();
        for (Ocorrencia o : ocorrencias) {
            if (SITUACAO_CRIADO.equals(o.situacao)) {
                criados++;
            } else if (SITUACAO_ATUALIZADO.equals(o.situacao)) {
                atualizados++;
            } else if (SITUACAO_JA_EXISTENTE.equals(o.situacao)) {
                existentes++;
            }
            anomalias.add(new AnaliseResultadoResponseDTO.Anomalia(o.tipo, o.gravidade, o.inicio, o.fim,
                    o.volume, o.idDispositivo, o.motivo, o.situacao, o.idAlerta));
        }
        if (anomalias.isEmpty()) {
            observacoes.add("Nenhuma anomalia encontrada na janela analisada.");
        }
        return new AnaliseResultadoResponseDTO(idResidencia, LocalDateTime.now(), inicioJanela, fimJanela,
                leiturasAnalisadas, perfilConfiavel, criados, atualizados, existentes, resolvidos, anomalias,
                observacoes);
    }

    // ------------------------------------------------------------------ apoio

    /** Ocorrência de um fluxo: começa no início do fluxo e termina no fim dele. */
    private Ocorrencia nova(Fluxo fluxo) {
        Ocorrencia ocorrencia = new Ocorrencia();
        ocorrencia.inicio = fluxo.inicio;
        ocorrencia.fim = fluxo.fim;
        ocorrencia.idDispositivo = fluxo.idDispositivo;
        return ocorrencia;
    }

    /**
     * Ocorrência de um grupo de leituras: começa na primeira e termina no fim da última a terminar.
     */
    private Ocorrencia nova(List<Leitura> grupo, int idDispositivo) {
        Ocorrencia ocorrencia = new Ocorrencia();
        ocorrencia.inicio = grupo.get(0).getDataLeitura();
        ocorrencia.fim = grupo.stream()
                .map(l -> l.getDataLeitura().plusSeconds(l.getDuracaoFluxoSeg()))
                .max(Comparator.naturalOrder())
                .orElse(ocorrencia.inicio);
        ocorrencia.idDispositivo = idDispositivo;
        return ocorrencia;
    }

    /** Soma os litros das leituras. */
    private double somarVolume(List<Leitura> leituras) {
        double total = 0;
        for (Leitura leitura : leituras) {
            total += leitura.getVolumeLitros();
        }
        return total;
    }

    /** Busca a residência ou lança "não encontrada". */
    private Residencia exigirResidencia(int idResidencia) {
        Residencia residencia = residenciaDAO.buscarPorId(idResidencia);
        if (residencia == null) {
            throw new IllegalArgumentException("Residência não encontrada");
        }
        return residencia;
    }

    private List<ResidenciaDAO.ResidenciaComVinculo> listarResidencias(int idMorador) {
        List<ResidenciaDAO.ResidenciaComVinculo> residencias = residenciaDAO.listarPorMorador(idMorador);
        if (residencias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência encontrada para o morador informado");
        }
        return residencias;
    }
}