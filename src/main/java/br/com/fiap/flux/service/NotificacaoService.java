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
 * Notificações dos alertas, sempre simuladas: nada é enviado por SMS, e-mail ou push, só o status
 * e a data de envio são registrados. O status de envio é independente do status do alerta.
 *
 * <p>Quando a análise gera um alerta, {@link #gerarParaAlerta} cria uma notificação PENDENTE para
 * cada morador ativo da residência (PUSH) e para cada contato com autorização ATIVA (SMS para
 * pessoa, EMAIL para associação). Quem já tem notificação do alerta é pulado. Os contatos (RN12)
 * recebem um texto reduzido, só com tipo, data e horário do alerta.</p>
 *
 * <p>Passagens de status permitidas: PENDENTE para ENVIADO ou FALHOU; ENVIADO para ENTREGUE ou
 * FALHOU; FALHOU para PENDENTE (nova tentativa). ENVIADO grava a data de envio e voltar a PENDENTE
 * a limpa.</p>
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

    // ---------- Geração (chamada pela análise) ----------

    /**
     * Cria as notificações PENDENTES de um alerta recém-gerado para os destinatários que ainda não
     * têm uma. Grava tudo ou nada.
     *
     * @return as notificações criadas (vazio se todos já tinham)
     */
    public List<Notificacao> gerarParaAlerta(Alerta alerta) {
        int idResidencia = alerta.getResidencia().getIdResidencia();
        Residencia residencia = residenciaDAO.buscarPorId(idResidencia);
        if (residencia == null) {
            return List.of();
        }

        List<Notificacao> existentes = notificacaoDAO.listarPorAlerta(alerta.getIdAlerta());
        List<Notificacao> novas = new ArrayList<>();
        novas.addAll(notificacoesDosMoradores(alerta, residencia, existentes));
        novas.addAll(notificacoesDosContatos(alerta, idResidencia, existentes));

        if (!novas.isEmpty()) {
            notificacaoDAO.cadastrarLote(novas);
        }
        return novas;
    }

    private List<Notificacao> notificacoesDosMoradores(Alerta alerta, Residencia residencia,
                                                       List<Notificacao> existentes) {
        String texto = montarTexto(alerta, residencia);
        List<Notificacao> novas = new ArrayList<>();
        for (MoradorResidencia vinculo : moradorResidenciaDAO.listarPorResidencia(residencia.getIdResidencia())) {
            Morador morador = vinculo.getMorador();
            if (morador.estaAtivo() && !jaNotificadoMorador(existentes, morador.getIdMorador())) {
                novas.add(nova(alerta, texto, CanalEnum.PUSH, morador, null));
            }
        }
        return novas;
    }

    private List<Notificacao> notificacoesDosContatos(Alerta alerta, int idResidencia,
                                                      List<Notificacao> existentes) {
        String texto = montarTextoContato(alerta);
        List<Notificacao> novas = new ArrayList<>();
        for (ContatoConfianca contato : listarContatosAutorizados(idResidencia)) {
            if (jaNotificadoContato(existentes, contato.getIdContato())) {
                continue;
            }
            CanalEnum canal = contato.getTipoContato() == TipoContatoEnum.ASSOCIACAO ? CanalEnum.EMAIL : CanalEnum.SMS;
            novas.add(nova(alerta, texto, canal, null, contato));
        }
        return novas;
    }

    // o mesmo contato pode ter sido autorizado por mais de um morador: conta uma vez só
    private List<ContatoConfianca> listarContatosAutorizados(int idResidencia) {
        Map<Integer, ContatoConfianca> contatos = new LinkedHashMap<>();
        for (AutorizacaoContato a : autorizacaoDAO.listarPorResidencia(idResidencia, StatusAutorizacaoEnum.ATIVA)) {
            if (a.estaAtiva()) {
                contatos.putIfAbsent(a.getContato().getIdContato(), a.getContato());
            }
        }
        return new ArrayList<>(contatos.values());
    }

    // ---------- Consultas ----------

    /** Notificações visíveis ao morador, com filtro opcional de status de envio. */
    public List<NotificacaoResponseDTO> listar(int idMorador, StatusEnvioEnum status) {
        return paraDtos(notificacaoDAO.listarVisiveis(idMorador, status));
    }

    /** Notificações PENDENTES visíveis ao morador. */
    public List<NotificacaoResponseDTO> listarPendentes(int idMorador) {
        return listar(idMorador, StatusEnvioEnum.PENDENTE);
    }

    public NotificacaoResponseDTO buscarPorId(int idNotificacao) {
        return paraDto(buscarExistente(idNotificacao));
    }

    /** Histórico de notificações de um alerta. */
    public List<NotificacaoResponseDTO> listarPorAlerta(int idAlerta) {
        alertaService.buscarExistente(idAlerta);
        return paraDtos(notificacaoDAO.listarPorAlerta(idAlerta));
    }

    // ---------- Registro e status ----------

    /**
     * Registra uma notificação PENDENTE de um alerta para um morador da residência ou para um
     * contato com autorização ativa (um dos dois, nunca os dois). O status é do servidor, e a
     * mensagem do contato é sempre a reduzida (RN12).
     */
    public NotificacaoResponseDTO registrar(int idMorador, NotificacaoRequestDTO dto) {
        validar(dto);
        verificarMorador(idMorador);
        Alerta alerta = alertaService.buscarExistente(dto.idAlerta());
        int idResidencia = alerta.getResidencia().getIdResidencia();
        List<Notificacao> existentes = notificacaoDAO.listarPorAlerta(alerta.getIdAlerta());

        Notificacao notificacao;
        if (dto.idMorador() != null) {
            Morador destinatario = verificarDestinatario(dto.idMorador(), idResidencia);
            exigirSemDuplicidade(existentes.stream()
                    .anyMatch(n -> enderecadaA(n, dto.idMorador()) && n.getCanal() == dto.canal()));
            notificacao = nova(alerta, dto.notificacao().trim(), dto.canal(), destinatario, null);
        } else {
            ContatoConfianca contato = verificarContato(dto.idContato(), idResidencia);
            exigirSemDuplicidade(existentes.stream()
                    .anyMatch(n -> enderecadaAoContato(n, dto.idContato()) && n.getCanal() == dto.canal()));
            // contato nunca recebe texto livre (RN12): o servidor monta a mensagem reduzida
            notificacao = nova(alerta, montarTextoContato(alerta), dto.canal(), null, contato);
        }

        notificacaoDAO.cadastrar(notificacao);
        if (notificacao.getIdNotificacao() == 0) {
            throw new IllegalStateException("Não foi possível gravar a notificação.");
        }
        return paraDto(buscarExistente(notificacao.getIdNotificacao()));
    }

    /**
     * Atualiza o status de envio (simulado), validando a passagem (409 se não é permitida). Não
     * mexe no status do alerta.
     */
    public NotificacaoResponseDTO atualizarStatus(int idNotificacao, StatusEnvioEnum novo) {
        if (novo == null) {
            throw new IllegalArgumentException("O status é obrigatório (PENDENTE, ENVIADO, ENTREGUE ou FALHOU).");
        }
        Notificacao notificacao = buscarExistente(idNotificacao);

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
        return paraDto(buscarExistente(idNotificacao));
    }

    // ---------- Textos ----------

    // gravidade, tipo, endereço, data e hora e, se houver, o volume; cortado em 300 caracteres
    private String montarTexto(Alerta alerta, Residencia residencia) {
        String gravidade = switch (alerta.getTipoGravidade()) {
            case CRITICO -> "crítico";
            case ATENCAO -> "de atenção";
        };
        StringBuilder texto = new StringBuilder("Alerta ").append(gravidade).append(": ")
                .append(descreverTipo(alerta)).append(" identificado em ")
                .append(residencia.getEndereco()).append(" em ")
                .append(alerta.getDataIdentificacao().format(DATA_HORA)).append(".");
        if (alerta.getVolumeDesperdicado() != null && alerta.getVolumeDesperdicado() > 0) {
            texto.append(String.format(PT_BR, " Volume estimado desperdiçado: %.0f L.", alerta.getVolumeDesperdicado()));
        }
        return texto.length() > TAMANHO_MAXIMO_TEXTO ? texto.substring(0, TAMANHO_MAXIMO_TEXTO) : texto.toString();
    }

    // RN12 e RNF11: só tipo, data e horário (sem endereço, gravidade, volume ou histórico)
    private String montarTextoContato(Alerta alerta) {
        return "Alerta de " + descreverTipo(alerta) + " em " + alerta.getDataIdentificacao().format(DATA)
                + " às " + alerta.getDataIdentificacao().format(HORA) + ".";
    }

    private String descreverTipo(Alerta alerta) {
        return switch (alerta.getTipoAlerta()) {
            case PONTO_ABERTO -> "ponto de água aberto";
            case VAZAMENTO_COMUM -> "vazamento contínuo";
            case FORA_DO_PADRAO -> "consumo fora do padrão";
        };
    }

    // ---------- auxiliares ----------

    private boolean transicaoPermitida(StatusEnvioEnum atual, StatusEnvioEnum novo) {
        return switch (atual) {
            case PENDENTE -> novo == StatusEnvioEnum.ENVIADO || novo == StatusEnvioEnum.FALHOU;
            case ENVIADO -> novo == StatusEnvioEnum.ENTREGUE || novo == StatusEnvioEnum.FALHOU;
            case FALHOU -> novo == StatusEnvioEnum.PENDENTE;
            case ENTREGUE -> false;
        };
    }

    // monta a notificação PENDENTE; alerta, morador e contato levam só o id
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

    private boolean enderecadaA(Notificacao notificacao, int idMorador) {
        return notificacao.getMorador() != null && notificacao.getMorador().getIdMorador() == idMorador;
    }

    private boolean enderecadaAoContato(Notificacao notificacao, int idContato) {
        return notificacao.getContato() != null && notificacao.getContato().getIdContato() == idContato;
    }

    private boolean jaNotificadoMorador(List<Notificacao> existentes, int idMorador) {
        for (Notificacao n : existentes) {
            if (enderecadaA(n, idMorador)) {
                return true;
            }
        }
        return false;
    }

    private boolean jaNotificadoContato(List<Notificacao> existentes, int idContato) {
        for (Notificacao n : existentes) {
            if (enderecadaAoContato(n, idContato)) {
                return true;
            }
        }
        return false;
    }

    private void exigirSemDuplicidade(boolean duplicada) {
        if (duplicada) {
            throw new IllegalArgumentException("Já existe uma notificação deste alerta para este destinatário e canal.");
        }
    }

    // ---------- Respostas ----------

    private NotificacaoResponseDTO paraDto(Notificacao notificacao) {
        return paraDtos(List.of(notificacao)).get(0);
    }

    // completa alerta, morador e contato (que o banco devolve só com o id); o alerta é carregado uma vez só
    private List<NotificacaoResponseDTO> paraDtos(List<Notificacao> notificacoes) {
        Map<Integer, Alerta> alertas = new HashMap<>();
        List<NotificacaoResponseDTO> dtos = new ArrayList<>();
        for (Notificacao notificacao : notificacoes) {
            completarAlerta(notificacao, alertas);
            completarMorador(notificacao);
            completarContato(notificacao);
            dtos.add(NotificacaoMapper.toDto(notificacao));
        }
        return dtos;
    }

    private void completarAlerta(Notificacao notificacao, Map<Integer, Alerta> alertas) {
        int idAlerta = notificacao.getAlerta().getIdAlerta();
        if (!alertas.containsKey(idAlerta)) {
            alertas.put(idAlerta, alertaService.completar(alertaService.buscarExistente(idAlerta)));
        }
        notificacao.setAlerta(alertas.get(idAlerta));
    }

    private void completarMorador(Notificacao notificacao) {
        if (notificacao.getMorador() == null) {
            return;
        }
        Morador morador = moradorDAO.buscarPorId(notificacao.getMorador().getIdMorador());
        if (morador != null) {
            notificacao.setMorador(morador);
        }
    }

    private void completarContato(Notificacao notificacao) {
        if (notificacao.getContato() == null) {
            return;
        }
        ContatoConfianca contato = contatoDAO.buscarPorId(notificacao.getContato().getIdContato());
        if (contato != null) {
            notificacao.setContato(contato);
        }
    }

    // ---------- Validações ----------

    private Notificacao buscarExistente(int idNotificacao) {
        Notificacao notificacao = notificacaoDAO.buscarPorId(idNotificacao);
        if (notificacao == null) {
            throw new IllegalArgumentException("Notificação não encontrada");
        }
        return notificacao;
    }

    private void verificarMorador(int idMorador) {
        if (moradorDAO.buscarPorId(idMorador) == null) {
            throw new IllegalArgumentException("Morador não encontrado");
        }
    }

    // o destinatário precisa ser morador ativo da residência do alerta
    private Morador verificarDestinatario(int idDestinatario, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idDestinatario, idResidencia);
        Morador destinatario = vinculo == null ? null : moradorDAO.buscarPorId(idDestinatario);
        if (destinatario == null || !destinatario.estaAtivo()) {
            throw new IllegalArgumentException("Destinatário inválido: morador ativo não encontrado nesta residência.");
        }
        return destinatario;
    }

    // o contato precisa existir e ter autorização ativa na residência do alerta
    private ContatoConfianca verificarContato(int idContato, int idResidencia) {
        ContatoConfianca contato = contatoDAO.buscarPorId(idContato);
        if (contato == null) {
            throw new IllegalArgumentException("Contato não encontrado");
        }
        for (ContatoConfianca autorizado : listarContatosAutorizados(idResidencia)) {
            if (autorizado.getIdContato() == idContato) {
                return contato;
            }
        }
        throw new IllegalArgumentException("O contato não tem autorização ativa nesta residência.");
    }

    private void validar(NotificacaoRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Os dados da notificação são obrigatórios.");
        }
        if (dto.notificacao() == null || dto.notificacao().isBlank()) {
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
}