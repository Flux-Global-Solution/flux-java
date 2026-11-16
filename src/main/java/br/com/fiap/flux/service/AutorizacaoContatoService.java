package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.AutorizacaoContatoDAO;
import br.com.fiap.flux.dao.ContatoConfiancaDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dto.AutorizacaoContatoRequestDTO;
import br.com.fiap.flux.dto.AutorizacaoContatoResponseDTO;
import br.com.fiap.flux.enums.StatusAutorizacaoEnum;
import br.com.fiap.flux.mapper.AutorizacaoContatoMapper;
import br.com.fiap.flux.models.AutorizacaoContato;
import br.com.fiap.flux.models.ContatoConfianca;
import br.com.fiap.flux.models.MoradorResidencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Autorizações para um contato de confiança receber avisos de uma residência. A autorização fica
 * presa ao vínculo (Morador_Residencia) do titular e só a ATIVA gera notificação. Revogar não apaga
 * nada: muda o status e registra a data, e a linha continua como histórico.
 *
 * <p>As operações com {@code idMorador} conferem se ele é titular da residência. As variantes só
 * com
 * o id da autorização (ou da residência) agem em nome do titular, que é resolvido internamente.</p>
 */
@Service
public class AutorizacaoContatoService {

    private final AutorizacaoContatoDAO autorizacaoDAO;
    private final ContatoConfiancaDAO contatoDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;

    public AutorizacaoContatoService() {
        this.autorizacaoDAO = new AutorizacaoContatoDAO();
        this.contatoDAO = new ContatoConfiancaDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
    }

    /**
     * Solicita a autorização de um contato numa residência. Nasce PENDENTE, sem datas, no vínculo
     * do
     * titular. O mesmo contato pode ser ligado a várias residências do mesmo titular, cada uma com
     * a sua
     * autorização.
     *
     * @throws ResponseStatusException 403 se não for o titular da residência ou o contato for de
     *     outro usuário
     * @throws IllegalArgumentException se contato ou residência faltam ou o contato não existe
     * @throws IllegalStateException se o banco não gravar a autorização
     */
    public AutorizacaoContatoResponseDTO solicitar(int idMorador, AutorizacaoContatoRequestDTO dto) {
        if (dto.idContato() == null || dto.idResidencia() == null) {
            System.out.println("ERRO: Contato e residência são obrigatórios");
            throw new IllegalArgumentException("O contato e a residência são obrigatórios.");
        }
        MoradorResidencia vinculo = exigirTitular(idMorador, dto.idResidencia());

        ContatoConfianca contato = contatoDAO.buscarPorId(dto.idContato());
        if (contato == null) {
            throw new IllegalArgumentException("Contato não encontrado");
        }
        int total = contatoDAO.contarAutorizacoes(dto.idContato());
        if (total > 0 && contatoDAO.contarAutorizacoesDoMorador(dto.idContato(), idMorador) != total) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso a este contato.");
        }

        AutorizacaoContato autorizacao = new AutorizacaoContato();
        autorizacao.setStatusAutorizacao(StatusAutorizacaoEnum.PENDENTE);   // status e datas nunca vêm do cliente
        autorizacao.setContato(contato);
        autorizacao.setMoradorResidencia(vinculo);
        autorizacaoDAO.cadastrar(autorizacao);
        if (autorizacao.getIdAutorizacao() == 0) {
            throw new IllegalStateException("Não foi possível gravar a autorização.");
        }
        return buscarPorId(idMorador, autorizacao.getIdAutorizacao());
    }

    /**
     * Lista as autorizações da residência (só o titular vê). Com {@code status}, PENDENTE traz as
     * aguardando ativação e ATIVA traz os contatos que recebem notificações.
     *
     * @throws ResponseStatusException 403 se não for o titular
     */
    public List<AutorizacaoContatoResponseDTO> listarPorResidencia(int idMorador, int idResidencia,
                                                                   StatusAutorizacaoEnum status) {
        exigirTitular(idMorador, idResidencia);
        return autorizacaoDAO.listarPorResidencia(idResidencia, status)
                .stream()
                .map(AutorizacaoContatoMapper::toDto)
                .toList();
    }

    /**
     * Consulta uma autorização; só o titular da residência dela pode.
     *
     * @throws ResponseStatusException 403 se não for o titular
     * @throws IllegalArgumentException se a autorização não existe
     */
    public AutorizacaoContatoResponseDTO buscarPorId(int idMorador, int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);
        exigirTitular(idMorador, autorizacao.getMoradorResidencia().getResidencia().getIdResidencia());
        return AutorizacaoContatoMapper.toDto(autorizacao);
    }

    /**
     * Ativa a autorização e registra a data. Vale para PENDENTE e também para REVOGADA (nova
     * autorização
     * do mesmo contato, já que o banco não permite uma segunda linha); nesse caso a data de
     * revogação é
     * limpa, como o CHECK da tabela exige.
     *
     * @throws ResponseStatusException 403 se não for o titular; 409 se a autorização já está ativa
     */
    public AutorizacaoContatoResponseDTO ativar(int idMorador, int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);
        exigirTitular(idMorador, autorizacao.getMoradorResidencia().getResidencia().getIdResidencia());

        if (autorizacao.estaAtiva()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A autorização já está ativa.");
        }
        autorizacao.ativar();
        autorizacaoDAO.alterar(autorizacao);
        return AutorizacaoContatoMapper.toDto(buscarExistente(idAutorizacao));
    }

    /**
     * Revoga a autorização: status REVOGADA e data de revogação preenchida. A linha permanece como
     * histórico.
     *
     * @throws ResponseStatusException 403 se não for o titular; 409 se já foi revogada
     */
    public AutorizacaoContatoResponseDTO revogar(int idMorador, int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);
        exigirTitular(idMorador, autorizacao.getMoradorResidencia().getResidencia().getIdResidencia());

        if (autorizacao.getStatusAutorizacao() == StatusAutorizacaoEnum.REVOGADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A autorização já foi revogada.");
        }
        autorizacao.revogar();
        autorizacaoDAO.alterar(autorizacao);
        return AutorizacaoContatoMapper.toDto(buscarExistente(idAutorizacao));
    }

    /**
     * Variante de {@link #listarPorResidencia(int, int, StatusAutorizacaoEnum)} sem {@code
     * idMorador}:
     * age em nome do titular da residência.
     */
    public List<AutorizacaoContatoResponseDTO> listarPorResidencia(int idResidencia, StatusAutorizacaoEnum status) {
        return listarPorResidencia(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia, status);
    }

    /** Variante de {@link #buscarPorId(int, int)} sem {@code idMorador}: age em nome do titular. */
    public AutorizacaoContatoResponseDTO buscarPorId(int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);
        int idResidencia = autorizacao.getMoradorResidencia().getResidencia().getIdResidencia();
        return buscarPorId(moradorResidenciaDAO.buscarIdTitular(idResidencia), idAutorizacao);
    }

    /** Variante de {@link #ativar(int, int)} sem {@code idMorador}: age em nome do titular. */
    public AutorizacaoContatoResponseDTO ativar(int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);
        int idResidencia = autorizacao.getMoradorResidencia().getResidencia().getIdResidencia();
        return ativar(moradorResidenciaDAO.buscarIdTitular(idResidencia), idAutorizacao);
    }

    /** Variante de {@link #revogar(int, int)} sem {@code idMorador}: age em nome do titular. */
    public AutorizacaoContatoResponseDTO revogar(int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);
        int idResidencia = autorizacao.getMoradorResidencia().getResidencia().getIdResidencia();
        return revogar(moradorResidenciaDAO.buscarIdTitular(idResidencia), idAutorizacao);
    }


    /** Busca a autorização ou lança "não encontrada". */
    private AutorizacaoContato buscarExistente(int idAutorizacao) {
        AutorizacaoContato autorizacao = autorizacaoDAO.buscarPorId(idAutorizacao);
        if (autorizacao == null) {
            throw new IllegalArgumentException("Autorização não encontrada");
        }
        return autorizacao;
    }

    /**
     * Devolve o vínculo do morador com a residência se ele for titular; senão 403 (inclui
     * residência
     * de terceiros).
     */
    private MoradorResidencia exigirTitular(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);
        if (vinculo == null || !vinculo.isTitular()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas o titular da residência pode gerenciar as autorizações de contatos.");
        }
        return vinculo;
    }
}