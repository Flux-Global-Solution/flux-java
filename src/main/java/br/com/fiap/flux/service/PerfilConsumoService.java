package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.LeituraDAO;
import br.com.fiap.flux.dao.PerfilConsumoDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.PerfilConsumoResponseDTO;
import br.com.fiap.flux.mapper.PerfilConsumoMapper;
import br.com.fiap.flux.models.Leitura;
import br.com.fiap.flux.models.PerfilConsumo;
import br.com.fiap.flux.models.Residencia;
import br.com.fiap.flux.util.ConsumoUtil;
import br.com.fiap.flux.util.ConsumoUtil.Periodo;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cálculo e consulta do perfil de consumo das residências, feitos só com as leituras do banco (sem
 * IA externa e sem o morador informar valores). Cada residência tem um único perfil: calcular de
 * novo
 * atualiza o existente. O perfil só é refeito quando chegam leituras novas ou quando o titular
 * pede;
 * consultar nunca recalcula.
 *
 * <p>Como o perfil é calculado:</p>
 * <ul>
 *   <li>consumo diário = soma dos litros de cada dia, entre o primeiro e o último dia com leitura
 *       (no máximo os últimos 90 dias); dia sem leitura conta como zero;</li>
 *   <li>dias dentro dos períodos de ausência da residência ficam de fora, junto com as leituras
 *       deles;</li>
 *   <li>dia atípico (mais de 3 vezes a mediana dos dias) também fica de fora,
 *       para que uma anomalia não vire o "habitual" com o qual ela mesma seria comparada;</li>
 *   <li>consumo médio e desvio padrão (amostral) saem dos totais diários que sobraram; a vazão
 *       média
 *       é o total de litros dividido pelo total de minutos de fluxo;</li>
 *   <li>histórico suficiente = ao menos 7 dias com consumo; se não, o perfil
 *       é gravado mesmo assim, mas marcado como pouco confiável.</li>
 * </ul>
 *
 * <p>Os períodos de ausência são lidos do texto da residência: datas em "2026-11-08" ou
 * "08/11/2026", em pares (início e fim) separados por qualquer texto, como "2026-11-08 a
 * 2026-11-12".</p>
 */
@Service
public class PerfilConsumoService {

    private static final int MINIMO_DIAS_HISTORICO = 7;
    private static final int MAXIMO_DIAS_ANALISADOS = 90;
    private static final double FATOR_DIA_ATIPICO = 3.0;
    private static final double FATOR_FIM_DE_SEMANA = 1.25;
    private static final int TAMANHO_MAXIMO_PADRAO = 255;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final PerfilConsumoDAO perfilDAO;
    private final LeituraDAO leituraDAO;
    private final ResidenciaDAO residenciaDAO;

    public PerfilConsumoService() {
        this.perfilDAO = new PerfilConsumoDAO();
        this.leituraDAO = new LeituraDAO();
        this.residenciaDAO = new ResidenciaDAO();
    }

    /** Consulta o perfil já gravado da residência, sem recalcular. */
    public PerfilConsumoResponseDTO buscarPorResidencia(int idResidencia) {
        exigirResidencia(idResidencia);
        PerfilConsumo perfil = perfilDAO.buscarPorResidencia(idResidencia);
        if (perfil == null) {
            throw new IllegalArgumentException("Perfil de consumo não encontrado: ainda não foi calculado "
                    + "para esta residência (use POST /residencias/" + idResidencia + "/perfil-consumo/calcular).");
        }
        return PerfilConsumoMapper.toDto(perfil);
    }

    /** Consulta o perfil pelo identificador, sem recalcular. */
    public PerfilConsumoResponseDTO buscarPorId(int idPerfilConsumo) {
        return PerfilConsumoMapper.toDto(buscarExistente(idPerfilConsumo));
    }

    /** Gera o perfil da residência ou, se já existe, recalcula o mesmo. */
    public PerfilConsumoResponseDTO calcular(int idResidencia) {
        return PerfilConsumoMapper.toDto(recalcular(exigirResidencia(idResidencia)));
    }

    /**
     * Atualiza o perfil recalculando-o com as leituras de agora. Os valores nunca vêm do cliente:
     * consumo médio, vazão e desvio não podem ser informados à mão.
     */
    public PerfilConsumoResponseDTO atualizar(int idPerfilConsumo) {
        PerfilConsumo perfil = buscarExistente(idPerfilConsumo);
        return PerfilConsumoMapper.toDto(recalcular(exigirResidencia(perfil.getResidencia().getIdResidencia())));
    }

    /**
     * Gera ou atualiza o perfil quando entram leituras novas na residência. Não confere acesso:
     * quem
     * chama já conferiu. Residência inexistente é ignorada.
     */
    public void atualizarAposNovasLeituras(int idResidencia) {
        Residencia residencia = residenciaDAO.buscarPorId(idResidencia);
        if (residencia != null) {
            recalcular(residencia);
        }
    }

    /**
     * Calcula o perfil e grava: atualiza o único perfil da residência ou cria o primeiro.
     *
     * @throws IllegalStateException se o banco não gravar o primeiro perfil
     */
    private PerfilConsumo recalcular(Residencia residencia) {
        PerfilConsumo novo = calcularPerfil(residencia);
        PerfilConsumo existente = perfilDAO.buscarPorResidencia(residencia.getIdResidencia());
        if (existente == null) {
            perfilDAO.cadastrar(novo);
            if (novo.getIdPerfilConsumo() == 0) {
                throw new IllegalStateException("Não foi possível gravar o perfil de consumo.");
            }
        } else {
            novo.setIdPerfilConsumo(existente.getIdPerfilConsumo());
            perfilDAO.alterar(novo);
        }
        return buscarExistente(novo.getIdPerfilConsumo());
    }

    /**
     * Monta o perfil a partir das leituras da residência, seguindo as regras descritas na classe.
     * Sem
     * leituras (fora dos períodos de ausência), devolve um perfil vazio e pouco confiável.
     */
    private PerfilConsumo calcularPerfil(Residencia residencia) {
        List<Periodo> ausencias = ConsumoUtil.lerAusencias(residencia.getPeriodoAusencia());
        List<Leitura> validas = leituraDAO.listarFiltrado(residencia.getIdResidencia(), null, null, null, null)
                .stream()
                .filter(l -> !ConsumoUtil.emAusencia(ausencias, l.getDataLeitura().toLocalDate()))
                .toList();

        PerfilConsumo perfil = new PerfilConsumo();
        perfil.setResidencia(residencia);
        perfil.setUltimaAtualizacao(LocalDate.now());
        if (validas.isEmpty()) {
            perfil.setPadraoConsumo("Sem leituras para identificar o padrão de consumo.");
            perfil.setHistoricoSuficiente(0);
            return perfil;
        }

        // Totais por dia entre o primeiro e o último dia com leitura (limitado aos últimos 90 dias).
        LocalDate ultimo = validas.get(validas.size() - 1).getDataLeitura().toLocalDate();
        LocalDate primeiro = validas.get(0).getDataLeitura().toLocalDate();
        LocalDate inicio = primeiro.isBefore(ultimo.minusDays(MAXIMO_DIAS_ANALISADOS - 1))
                ? ultimo.minusDays(MAXIMO_DIAS_ANALISADOS - 1) : primeiro;
        Map<LocalDate, Double> totalPorDia = new TreeMap<>();
        for (LocalDate dia = inicio; !dia.isAfter(ultimo); dia = dia.plusDays(1)) {
            if (!ConsumoUtil.emAusencia(ausencias, dia)) {
                totalPorDia.put(dia, 0.0);
            }
        }
        for (Leitura leitura : validas) {
            double litros = leitura.getVolumeLitros();
            totalPorDia.computeIfPresent(leitura.getDataLeitura().toLocalDate(), (dia, total) -> total + litros);
        }

        // Dias atípicos (muito acima da mediana) não entram na referência.
        double mediana = mediana(new ArrayList<>(totalPorDia.values()));
        Set<LocalDate> diasAtipicos = new HashSet<>();
        if (mediana > 0) {
            totalPorDia.forEach((dia, total) -> {
                if (total > mediana * FATOR_DIA_ATIPICO) {
                    diasAtipicos.add(dia);
                }
            });
        }
        Map<LocalDate, Double> totaisBase = new TreeMap<>(totalPorDia);
        totaisBase.keySet().removeAll(diasAtipicos);
        List<Double> totais = new ArrayList<>(totaisBase.values());

        double consumoMedio = media(totais);
        double desvioPadrao = desvioPadrao(totais, consumoMedio);

        List<Leitura> base = validas.stream()
                .filter(l -> totaisBase.containsKey(l.getDataLeitura().toLocalDate()))
                .toList();
        double litros = 0;
        double minutos = 0;
        double[] litrosPorHora = new double[24];
        for (Leitura leitura : base) {
            litros += leitura.getVolumeLitros();
            minutos += leitura.getDuracaoFluxoSeg() / 60.0;
            litrosPorHora[leitura.getDataLeitura().getHour()] += leitura.getVolumeLitros();
        }
        double vazaoMedia = minutos > 0 ? litros / minutos : 0;

        long diasComConsumo = totais.stream().filter(total -> total > 0).count();
        boolean suficiente = diasComConsumo >= MINIMO_DIAS_HISTORICO;

        perfil.setConsumoMedio(ConsumoUtil.arredondar(consumoMedio));
        perfil.setVazaoMedia(ConsumoUtil.arredondar(vazaoMedia));
        perfil.setDesvioPadrao(ConsumoUtil.arredondar(desvioPadrao));
        perfil.setHistoricoSuficiente(suficiente ? 1 : 0);
        perfil.setPadraoConsumo(descreverPadrao(residencia, consumoMedio, litrosPorHora, totaisBase,
                diasAtipicos.size(), diasComConsumo, suficiente));
        return perfil;
    }

    /**
     * Monta o texto do padrão de consumo: ressalva de histórico insuficiente, faixa de duas horas
     * de maior
     * consumo, fins de semana acima ou abaixo da semana, consumo médio por morador e dias atípicos.
     * O texto
     * é cortado no tamanho da coluna.
     */
    private String descreverPadrao(Residencia residencia, double consumoMedio, double[] litrosPorHora,
                                   Map<LocalDate, Double> totaisBase, int diasAtipicos,
                                   long diasComConsumo, boolean suficiente) {
        List<String> partes = new ArrayList<>();
        if (!suficiente) {
            partes.add("Histórico insuficiente (" + diasComConsumo + " de " + MINIMO_DIAS_HISTORICO
                    + " dias): referência pouco confiável");
        }

        int melhorHora = -1;
        double melhorTotal = 0;
        for (int hora = 0; hora < 23; hora++) {
            double total = litrosPorHora[hora] + litrosPorHora[hora + 1];
            if (total > melhorTotal) {
                melhorTotal = total;
                melhorHora = hora;
            }
        }
        if (melhorHora >= 0) {
            partes.add(String.format(PT_BR, "Maior consumo entre %02dh e %02dh", melhorHora, melhorHora + 2));
        }

        double somaFimDeSemana = 0;
        double somaSemana = 0;
        int diasFimDeSemana = 0;
        int diasSemana = 0;
        for (Map.Entry<LocalDate, Double> dia : totaisBase.entrySet()) {
            DayOfWeek diaDaSemana = dia.getKey().getDayOfWeek();
            if (diaDaSemana == DayOfWeek.SATURDAY || diaDaSemana == DayOfWeek.SUNDAY) {
                somaFimDeSemana += dia.getValue();
                diasFimDeSemana++;
            } else {
                somaSemana += dia.getValue();
                diasSemana++;
            }
        }
        if (diasFimDeSemana >= 2 && diasSemana >= 2 && somaSemana > 0) {
            double mediaFimDeSemana = somaFimDeSemana / diasFimDeSemana;
            double mediaSemana = somaSemana / diasSemana;
            if (mediaFimDeSemana >= mediaSemana * FATOR_FIM_DE_SEMANA) {
                partes.add("Consumo maior nos fins de semana");
            } else if (mediaFimDeSemana <= mediaSemana / FATOR_FIM_DE_SEMANA) {
                partes.add("Consumo menor nos fins de semana");
            }
        }

        int moradores = Math.max(1, residencia.getQtdMoradores());
        partes.add(String.format(PT_BR, "Média de %.0f L por morador/dia", consumoMedio / moradores));
        if (diasAtipicos > 0) {
            partes.add(diasAtipicos + " dia(s) atípico(s) fora da referência");
        }

        String texto = String.join("; ", partes) + ".";
        return texto.length() > TAMANHO_MAXIMO_PADRAO ? texto.substring(0, TAMANHO_MAXIMO_PADRAO) : texto;
    }

    private double media(List<Double> valores) {
        return valores.isEmpty() ? 0 : valores.stream().mapToDouble(Double::doubleValue).sum() / valores.size();
    }

    /** Desvio padrão amostral (n - 1); com menos de dois dias vale zero. */
    private double desvioPadrao(List<Double> valores, double media) {
        if (valores.size() < 2) {
            return 0;
        }
        double soma = 0;
        for (double valor : valores) {
            soma += (valor - media) * (valor - media);
        }
        return Math.sqrt(soma / (valores.size() - 1));
    }

    /** Mediana dos valores (ordena a lista recebida); lista vazia vale zero. */
    private double mediana(List<Double> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        Collections.sort(valores);
        int meio = valores.size() / 2;
        return valores.size() % 2 == 1 ? valores.get(meio) : (valores.get(meio - 1) + valores.get(meio)) / 2;
    }

    /** Busca a residência ou lança "não encontrada". */
    private Residencia exigirResidencia(int idResidencia) {
        Residencia residencia = residenciaDAO.buscarPorId(idResidencia);
        if (residencia == null) {
            throw new IllegalArgumentException("Residência não encontrada");
        }
        return residencia;
    }

    /** Busca o perfil ou lança "não encontrado". */
    private PerfilConsumo buscarExistente(int idPerfilConsumo) {
        PerfilConsumo perfil = perfilDAO.buscarPorId(idPerfilConsumo);
        if (perfil == null) {
            throw new IllegalArgumentException("Perfil de consumo não encontrado");
        }
        return perfil;
    }
}