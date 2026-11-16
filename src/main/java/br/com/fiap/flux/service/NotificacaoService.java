package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.AutorizacaoContatoDAO;
import br.com.fiap.flux.dao.ContatoConfiancaDAO;
import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dao.NotificacaoDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.NotificacaoRequestDTO;
import br.com.fiap.flux.dto.NotificacaoResponseDTO;
import br.com.fiap.flux.enums.CanalEnum;
import br.com.fiap.flux.enums.StatusAutorizacaoEnum;
import br.com.fiap.flux.enums.StatusEnvioEnum;
import br.com.fiap.flux.enums.TipoContatoEnum;
import br.com.fiap.flux.mapper.NotificacaoMapper;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.AutorizacaoContato;
import br.com.fiap.flux.models.ContatoConfianca;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Notificacao;
import br.com.fiap.flux.models.Residencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Notificações dos alertas, sempre simuladas: nada é enviado por SMS, e-mail ou push, só o status e
 * a
 * data de envio são registrados. O status de envio é independente do status do alerta.
 *
 * <p>Quando a análise gera um alerta, {@link #gerarParaAlerta} cria uma notificação PENDENTE para
 * cada
 * morador ativo da residência (canal PUSH) e para cada contato com autorização ATIVA (SMS para
 * pessoa,
 * EMAIL para associação). Contato sem autorização ativa nunca recebe. Os contatos (RN12) recebem um
 * texto reduzido, só com tipo, data e horário do alerta: endereço, gravidade e volume desperdiçado
 * ficam só com os moradores. A geração não repete (quem já tem notificação do alerta é pulado) e as
 * consultas, inclusive as do dashboard, nunca criam nada.</p>
 *
 * <p>Passagens de status permitidas: {@code PENDENTE -> ENVIADO} ou {@code FALHOU};
 * {@code ENVIADO -> ENTREGUE} ou {@code FALHOU}; {@code FALHOU -> PENDENTE} (nova tentativa).
 * ENVIADO grava a data de envio; voltar a PENDENTE a limpa.</p>
 *
 * <p>As variantes só com o id da notificação ou do alerta agem em nome do titular, que é resolvido
 * internamente.</p>
 */
@Service
public class NotificacaoService {

    private static final int TAMANHO_MAXIMO_TEXTO = 300;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificacaoDAO notificacaoDAO;
    private final AlertaService alertaService;
    private final ResidenciaDAO residenciaDAO;
    private final MoradorDAO moradorDAO;
    private final ContatoConfiancaDAO contatoDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;
    private final AutorizacaoContatoDAO autorizacaoDAO;

    public NotificacaoService() {
        this.notificacaoDAO = new NotificacaoDAO();
        this.alertaService = new AlertaService();
        this.residenciaDAO = new ResidenciaDAO();
        this.moradorDAO = new MoradorDAO();
        this.contatoDAO = new ContatoConfiancaDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
        this.autorizacaoDAO = new AutorizacaoContatoDAO();
    }

    // ------------------------------------------------------------------ geração

    /**
     * Cria as notificações PENDENTES de um alerta recém-gerado para os destinatários elegíveis que
     * ainda
     * não têm uma. É chamado pela análise e não confere acesso. Grava tudo ou nada.
     *
     * @return as notificações criadas (vazio se todos os elegíveis já tinham)
     */
    public List<Notificacao> gerarParaAlerta(Alerta alerta) {
        int idResidencia = alerta.getResidencia().getIdResidencia();
        Residencia residencia = residenciaDAO.buscarPorId(idResidencia);
        if (residencia == null) {
            return List.of();
        }
        String texto = montarTexto(alerta, residencia);
        String textoContato = montarTextoContato(alerta);
        List<Notificacao> existentes = notificacaoDAO.listarPorAlerta(alerta.getIdAlerta());
        List<Notificacao> novas = new ArrayList<>();

        for (MoradorResidencia vinculo : moradorResidenciaDAO.listarPorResidencia(idResidencia)) {
            Morador morador = vinculo.getMorador();
            if (!morador.estaAtivo() || jaNotificadoMorador(existentes, morador.getIdMorador())) {
                continue;
            }
            novas.add(nova(alerta, texto, CanalEnum.PUSH, morador, null));
        }

        // o mesmo contato pode ter sido autorizado por mais de um morador: uma notificação só
        Map<Integer, ContatoConfianca> contatos = new LinkedHashMap<>();
        for (AutorizacaoContato autorizacao : autorizacaoDAO.listarPorResidencia(idResidencia, StatusAutorizacaoEnum.ATIVA)) {
            if (autorizacao.estaAtiva()) {
                contatos.putIfAbsent(autorizacao.getContato().getIdContato(), autorizacao.getContato());
            }
        }
        for (ContatoConfianca contato : contatos.values()) {
            if (jaNotificadoContato(existentes, contato.getIdContato())) {
                continue;
            }
            CanalEnum canal = contato.getTipoContato() == TipoContatoEnum.ASSOCIACAO ? CanalEnum.EMAIL : CanalEnum.SMS;
            novas.add(nova(alerta, textoContato, canal, null, contato));
        }

        if (!novas.isEmpty()) {
            notificacaoDAO.cadastrarLote(novas);
        }
        return novas;
    }

    // ------------------------------------------------------------------ consultas

    /**
     * Notificações visíveis ao morador (as dele e, se titular, as da residência), com filtro
     * opcional de
     * status.
     */
    public List<NotificacaoResponseDTO> listar(int idMorador, StatusEnvioEnum status) {
        return paraDtos(notificacaoDAO.listarVisiveis(idMorador, status));
    }

    /** Notificações PENDENTES visíveis ao morador. */
    public List<NotificacaoResponseDTO> listarPendentes(int idMorador) {
        return listar(idMorador, StatusEnvioEnum.PENDENTE);
    }

    /**
     * Consulta uma notificação: a endereçada ao morador ou, se ele é titular, qualquer uma da
     * residência.
     *
     * @throws ResponseStatusException 403 se não for dele nem titular da residência
     * @throws IllegalArgumentException se a notificação não existe
     */
    public NotificacaoResponseDTO buscarPorId(int idMorador, int idNotificacao) {
        Notificacao notificacao = buscarExistente(idNotificacao);
        Alerta alerta = alertaService.buscarExistente(notificacao.getAlerta().getIdAlerta());
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, alerta.getResidencia().getIdResidencia());
        if (!enderecadaA(notificacao, idMorador) && (vinculo == null || !vinculo.isTitular())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso a esta notificação.");
        }
        return paraDtos(List.of(notificacao)).get(0);
    }

    /**
     * Histórico de notificações de um alerta: o titular vê todas; o morador comum, só as dele.
     *
     * @throws ResponseStatusException 403 se não houver vínculo com a residência do alerta
     */
    public List<NotificacaoResponseDTO> listarPorAlerta(int idMorador, int idAlerta) {
        Alerta alerta = alertaService.buscarExistente(idAlerta);
        MoradorResidencia vinculo = exigirVinculo(idMorador, alerta.getResidencia().getIdResidencia());
        List<Notificacao> notificacoes = notificacaoDAO.listarPorAlerta(idAlerta);
        if (!vinculo.isTitular()) {
            notificacoes = notificacoes.stream().filter(n -> enderecadaA(n, idMorador)).toList();
        }
        return paraDtos(notificacoes);
    }

    // ------------------------------------------------------------------ registro e status

    /**
     * Registra uma notificação PENDENTE de um alerta para um morador da residência ou para um
     * contato com
     * autorização ativa (um dos dois, nunca os dois). Só o titular. Status e data de envio são do
     * servidor, e a mensagem do contato é sempre a reduzida, montada aqui (RN12).
     *
     * @throws ResponseStatusException 403 se não for o titular
     * @throws IllegalArgumentException se os dados são inválidos, o destinatário não é elegível ou
     *     a notificação já existe para o destinatário e o canal
     * @throws IllegalStateException se o banco não gravar a notificação
     */
    public NotificacaoResponseDTO registrar(int idMorador, NotificacaoRequestDTO dto) {
        validar(dto);
        Alerta alerta = alertaService.buscarExistente(dto.idAlerta());
        int idResidencia = alerta.getResidencia().getIdResidencia();
        exigirTitular(idMorador, idResidencia, "Apenas o titular da residência pode registrar notificações.");

        Notificacao notificacao = new Notificacao();
        // contato nunca recebe texto livre (RN12): o servidor monta a mensagem reduzida
        notificacao.setNotificacao(dto.idContato() != null
                ? montarTextoContato(alerta) : dto.notificacao().trim());
        notificacao.setCanal(dto.canal());
        notificacao.setStatusEnvio(StatusEnvioEnum.PENDENTE);
        Alerta referencia = new Alerta();
        referencia.setIdAlerta(alerta.getIdAlerta());
        notificacao.setAlerta(referencia);

        List<Notificacao> existentes = notificacaoDAO.listarPorAlerta(alerta.getIdAlerta());
        if (dto.idMorador() != null) {
            MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(dto.idMorador(), idResidencia);
            Morador destinatario = vinculo == null ? null : moradorDAO.buscarPorId(dto.idMorador());
            if (destinatario == null || !destinatario.estaAtivo()) {
                throw new IllegalArgumentException("Destinatário inválido: morador ativo não encontrado nesta residência.");
            }
            exigirSemDuplicidade(existentes.stream().anyMatch(n -> enderecadaA(n, dto.idMorador())
                    && n.getCanal() == dto.canal()));
            Morador referenciaMorador = new Morador();
            referenciaMorador.setIdMorador(dto.idMorador());
            notificacao.setMorador(referenciaMorador);
        } else {
            if (contatoDAO.buscarPorId(dto.idContato()) == null) {
                throw new IllegalArgumentException("Contato não encontrado");
            }
            boolean autorizado = autorizacaoDAO.listarPorResidencia(idResidencia, StatusAutorizacaoEnum.ATIVA).stream()
                    .anyMatch(a -> a.estaAtiva() && a.getContato().getIdContato() == dto.idContato());
            if (!autorizado) {
                throw new IllegalArgumentException("O contato não tem autorização ativa nesta residência.");
            }
            exigirSemDuplicidade(existentes.stream().anyMatch(n -> n.getContato() != null
                    && n.getContato().getIdContato() == dto.idContato() && n.getCanal() == dto.canal()));
            ContatoConfianca referenciaContato = new ContatoConfianca();
            referenciaContato.setIdContato(dto.idContato());
            notificacao.setContato(referenciaContato);
        }

        notificacaoDAO.cadastrar(notificacao);
        if (notificacao.getIdNotificacao() == 0) {
            throw new IllegalStateException("Não foi possível gravar a notificação.");
        }
        return paraDtos(List.of(buscarExistente(notificacao.getIdNotificacao()))).get(0);
    }

    /**
     * Atualiza o status de envio (simulado), validando a passagem. Só o titular da residência do
     * alerta.
     * Não mexe no status do alerta.
     *
     * @throws ResponseStatusException 403 se não for o titular; 409 se a passagem não é permitida
     * @throws IllegalArgumentException se o status não vem ou a notificação não existe
     */
    public NotificacaoResponseDTO atualizarStatus(int idMorador, int idNotificacao, StatusEnvioEnum novo) {
        if (novo == null) {
            throw new IllegalArgumentException("O status é obrigatório (PENDENTE, ENVIADO, ENTREGUE ou FALHOU).");
        }
        Notificacao notificacao = buscarExistente(idNotificacao);
        Alerta alerta = alertaService.buscarExistente(notificacao.getAlerta().getIdAlerta());
        exigirTitular(idMorador, alerta.getResidencia().getIdResidencia(),
                "Apenas o titular da residência pode atualizar o status de envio.");

        StatusEnvioEnum atual = notificacao.getStatusEnvio();
        if (!transicaoPermitida(atual, novo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transição de status inválida: " + atual + " -> " + novo + ".");
        }
        notificacao.setStatusEnvio(novo);
        if (novo == StatusEnvioEnum.ENVIADO) {
            notificacao.setDataEnvio(LocalDate.now());
        } else if (novo == StatusEnvioEnum.PENDENTE) {
            notificacao.setDataEnvio(null);
        }
        notificacaoDAO.alterar(notificacao);
        return paraDtos(List.of(buscarExistente(idNotificacao))).get(0);
    }

    /** Variante de {@link #buscarPorId(int, int)} sem {@code idMorador}: age em nome do titular. */
    public NotificacaoResponseDTO buscarPorId(int idNotificacao) {
        Notificacao notificacao = buscarExistente(idNotificacao);
        Alerta alerta = alertaService.buscarExistente(notificacao.getAlerta().getIdAlerta());
        int idMorador = moradorResidenciaDAO.buscarIdTitular(alerta.getResidencia().getIdResidencia());
        return buscarPorId(idMorador, idNotificacao);
    }

    /**
     * Variante de {@link #listarPorAlerta(int, int)} sem {@code idMorador}: age em nome do titular.
     */
    public List<NotificacaoResponseDTO> listarPorAlerta(int idAlerta) {
        Alerta alerta = alertaService.buscarExistente(idAlerta);
        int idMorador = moradorResidenciaDAO.buscarIdTitular(alerta.getResidencia().getIdResidencia());
        return listarPorAlerta(idMorador, idAlerta);
    }

    /**
     * Variante de {@link #atualizarStatus(int, int, StatusEnvioEnum)} sem {@code idMorador}: age em
     * nome
     * do titular.
     */
    public NotificacaoResponseDTO atualizarStatus(int idNotificacao, StatusEnvioEnum novo) {
        Notificacao notificacao = buscarExistente(idNotificacao);
        Alerta alerta = alertaService.buscarExistente(notificacao.getAlerta().getIdAlerta());
        int idMorador = moradorResidenciaDAO.buscarIdTitular(alerta.getResidencia().getIdResidencia());
        return atualizarStatus(idMorador, idNotificacao, novo);
    }



    // ------------------------------------------------------------------ apoio

    /**
     * Diz se a passagem de {@code atual} para {@code novo} está entre as permitidas (ENTREGUE é
     * final).
     */
    private boolean transicaoPermitida(StatusEnvioEnum atual, StatusEnvioEnum novo) {
        return switch (atual) {
            case PENDENTE -> novo == StatusEnvioEnum.ENVIADO || novo == StatusEnvioEnum.FALHOU;
            case ENVIADO -> novo == StatusEnvioEnum.ENTREGUE || novo == StatusEnvioEnum.FALHOU;
            case FALHOU -> novo == StatusEnvioEnum.PENDENTE;
            case ENTREGUE -> false;
        };
    }

    /**
     * Monta uma notificação PENDENTE do alerta para um morador ou um contato (só os ids são
     * referenciados).
     */
    private Notificacao nova(Alerta alerta, String texto, CanalEnum canal, Morador morador, ContatoConfianca contato) {
        Notificacao notificacao = new Notificacao();
        notificacao.setNotificacao(texto);
        notificacao.setCanal(canal);
        notificacao.setStatusEnvio(StatusEnvioEnum.PENDENTE);
        Alerta referencia = new Alerta();
        referencia.setIdAlerta(alerta.getIdAlerta());
        notificacao.setAlerta(referencia);
        if (morador != null) {
            Morador referenciaMorador = new Morador();
            referenciaMorador.setIdMorador(morador.getIdMorador());
            notificacao.setMorador(referenciaMorador);
        }
        if (contato != null) {
            ContatoConfianca referenciaContato = new ContatoConfianca();
            referenciaContato.setIdContato(contato.getIdContato());
            notificacao.setContato(referenciaContato);
        }
        return notificacao;
    }

    /**
     * Texto completo, para o morador: gravidade, tipo, endereço, data e hora e, se houver, o volume
     * desperdiçado. É cortado em 300 caracteres, o tamanho da coluna.
     */
    private String montarTexto(Alerta alerta, Residencia residencia) {
        String gravidade = switch (alerta.getTipoGravidade()) {
            case CRITICO -> "crítico";
            case ATENCAO -> "de atenção";
        };
        String tipo = switch (alerta.getTipoAlerta()) {
            case PONTO_ABERTO -> "ponto de água aberto";
            case VAZAMENTO_COMUM -> "vazamento contínuo";
            case FORA_DO_PADRAO -> "consumo fora do padrão";
        };
        StringBuilder texto = new StringBuilder("Alerta ").append(gravidade).append(": ").append(tipo)
                .append(" identificado em ").append(residencia.getEndereco())
                .append(" em ").append(alerta.getDataIdentificacao().format(DATA_HORA)).append(".");
        if (alerta.getVolumeDesperdicado() != null && alerta.getVolumeDesperdicado() > 0) {
            texto.append(String.format(PT_BR, " Volume estimado desperdiçado: %.0f L.", alerta.getVolumeDesperdicado()));
        }
        return texto.length() > TAMANHO_MAXIMO_TEXTO ? texto.substring(0, TAMANHO_MAXIMO_TEXTO) : texto.toString();
    }

    /**
     * Texto reduzido para contato de confiança e associação (RN12 e RNF11): só o tipo, a data e o
     * horário. Não leva endereço, gravidade, volume desperdiçado nem histórico de consumo.
     */
    private String montarTextoContato(Alerta alerta) {
        String tipo = switch (alerta.getTipoAlerta()) {
            case PONTO_ABERTO -> "ponto de água aberto";
            case VAZAMENTO_COMUM -> "vazamento contínuo";
            case FORA_DO_PADRAO -> "consumo fora do padrão";
        };
        return "Alerta de " + tipo + " em " + alerta.getDataIdentificacao().format(DATA) + " às "
                + alerta.getDataIdentificacao().format(HORA) + ".";
    }

    /** Diz se a notificação é endereçada ao morador. */
    private boolean enderecadaA(Notificacao notificacao, int idMorador) {
        return notificacao.getMorador() != null && notificacao.getMorador().getIdMorador() == idMorador;
    }

    /** Diz se o morador já tem notificação na lista. */
    private boolean jaNotificadoMorador(List<Notificacao> existentes, int idMorador) {
        return existentes.stream().anyMatch(n -> enderecadaA(n, idMorador));
    }

    /** Diz se o contato já tem notificação na lista. */
    private boolean jaNotificadoContato(List<Notificacao> existentes, int idContato) {
        return existentes.stream().anyMatch(n -> n.getContato() != null && n.getContato().getIdContato() == idContato);
    }

    /**
     * Impede notificação repetida do mesmo alerta para o mesmo destinatário e canal.
     *
     * @throws IllegalArgumentException se {@code duplicada} for verdadeiro
     */
    private void exigirSemDuplicidade(boolean duplicada) {
        if (duplicada) {
            throw new IllegalArgumentException("Já existe uma notificação deste alerta para este destinatário e canal.");
        }
    }

    /**
     * Completa alerta, morador e contato (que o banco devolve só com o id) e monta as respostas,
     * reaproveitando o que já foi carregado.
     */
    private List<NotificacaoResponseDTO> paraDtos(List<Notificacao> notificacoes) {
        Map<Integer, Alerta> alertas = new HashMap<>();
        Map<Integer, Morador> moradores = new HashMap<>();
        Map<Integer, ContatoConfianca> contatos = new HashMap<>();
        List<NotificacaoResponseDTO> dtos = new ArrayList<>();
        for (Notificacao notificacao : notificacoes) {
            int idAlerta = notificacao.getAlerta().getIdAlerta();
            Alerta alerta = alertas.get(idAlerta);
            if (alerta == null) {
                alerta = alertaService.completar(alertaService.buscarExistente(idAlerta));
                alertas.put(idAlerta, alerta);
            }
            notificacao.setAlerta(alerta);
            if (notificacao.getMorador() != null) {
                int id = notificacao.getMorador().getIdMorador();
                Morador morador = moradores.get(id);
                if (morador == null) {
                    morador = moradorDAO.buscarPorId(id);
                    moradores.put(id, morador);
                }
                if (morador != null) {
                    notificacao.setMorador(morador);
                }
            }
            if (notificacao.getContato() != null) {
                int id = notificacao.getContato().getIdContato();
                ContatoConfianca contato = contatos.get(id);
                if (contato == null) {
                    contato = contatoDAO.buscarPorId(id);
                    contatos.put(id, contato);
                }
                if (contato != null) {
                    notificacao.setContato(contato);
                }
            }
            dtos.add(NotificacaoMapper.toDto(notificacao));
        }
        return dtos;
    }

    /** Busca a notificação ou lança "não encontrada". */
    private Notificacao buscarExistente(int idNotificacao) {
        Notificacao notificacao = notificacaoDAO.buscarPorId(idNotificacao);
        if (notificacao == null) {
            throw new IllegalArgumentException("Notificação não encontrada");
        }
        return notificacao;
    }

    /**
     * Confere texto (obrigatório, até 300 caracteres), canal, alerta e que
     * exatamente um destinatário (morador ou contato) tenha sido informado.
     *
     * @throws IllegalArgumentException na primeira regra violada
     */
    private void validar(NotificacaoRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Os dados da notificação são obrigatórios.");
        }
        if (dto.notificacao() == null || dto.notificacao().isBlank()) {
            System.out.println("ERRO: O texto da notificação é obrigatório");
            throw new IllegalArgumentException("O texto da notificação é obrigatório.");
        }
        if (dto.notificacao().trim().length() > TAMANHO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException("O texto da notificação deve ter no máximo " + TAMANHO_MAXIMO_TEXTO + " caracteres.");
        }
        if (dto.canal() == null) {
            throw new IllegalArgumentException("O canal é obrigatório (PUSH, EMAIL ou SMS).");
        }
        if (dto.idAlerta() < 1) {
            throw new IllegalArgumentException("O alerta é obrigatório.");
        }
        if ((dto.idMorador() == null) == (dto.idContato() == null)) {
            throw new IllegalArgumentException("Informe o destinatário: um morador ou um contato (apenas um dos dois).");
        }
    }

    /** Devolve o vínculo do morador com a residência; senão 403. */
    private MoradorResidencia exigirVinculo(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);
        if (vinculo == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso às notificações desta residência.");
        }
        return vinculo;
    }

    /**
     * Como {@link #exigirVinculo}, mas o vínculo precisa ser de titular; {@code mensagem} é o texto
     * do 403.
     */
    private void exigirTitular(int idMorador, int idResidencia, String mensagem) {
        if (!exigirVinculo(idMorador, idResidencia).isTitular()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensagem);
        }
    }
}