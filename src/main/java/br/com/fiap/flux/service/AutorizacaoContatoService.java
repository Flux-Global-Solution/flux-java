package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.AutorizacaoContatoDAO;
import br.com.fiap.flux.dao.ContatoConfiancaDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
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
 * presa ao vínculo (Morador_Residencia) do morador e só a ATIVA gera notificação. Revogar não apaga
 * nada: muda o status e registra a data, e a linha continua como histórico.
 * Solicitar uma autorização exige que o morador informado tenha vínculo com a residência.
 */
@Service
public class AutorizacaoContatoService {

    private final AutorizacaoContatoDAO autorizacaoDAO;
    private final ContatoConfiancaDAO contatoDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;
    private final ResidenciaDAO residenciaDAO;

    public AutorizacaoContatoService() {
        this.autorizacaoDAO = new AutorizacaoContatoDAO();
        this.contatoDAO = new ContatoConfiancaDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
        this.residenciaDAO = new ResidenciaDAO();
    }

    /**
     * Solicita a autorização de um contato numa residência. Nasce PENDENTE, sem datas, no vínculo do
     * morador. O mesmo contato pode ser ligado a várias residências do mesmo morador, cada uma com a
     * sua autorização.
     */
    public AutorizacaoContatoResponseDTO solicitar(int idMorador, AutorizacaoContatoRequestDTO dto) {
        if (dto.idContato() == null || dto.idResidencia() == null) {
            throw new IllegalArgumentException("O contato e a residência são obrigatórios.");
        }
        MoradorResidencia vinculo = verificarVinculo(idMorador, dto.idResidencia());

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
        return buscarPorId(autorizacao.getIdAutorizacao());
    }

    /**
     * Lista as autorizações da residência. Com {@code status}, PENDENTE traz as aguardando ativação e
     * ATIVA traz os contatos que recebem notificações.
     */
    public List<AutorizacaoContatoResponseDTO> listarPorResidencia(int idResidencia, StatusAutorizacaoEnum status) {
        if (residenciaDAO.buscarPorId(idResidencia) == null) {
            throw new IllegalArgumentException("Residência não encontrada");
        }
        return autorizacaoDAO.listarPorResidencia(idResidencia, status)
                .stream()
                .map(AutorizacaoContatoMapper::toDto)
                .toList();
    }

    /** Consulta uma autorização. */
    public AutorizacaoContatoResponseDTO buscarPorId(int idAutorizacao) {
        return AutorizacaoContatoMapper.toDto(buscarExistente(idAutorizacao));
    }

    /**
     * Ativa a autorização e registra a data. Vale para PENDENTE e também para REVOGADA (nova autorização
     * do mesmo contato, já que o banco não permite uma segunda linha); nesse caso a data de revogação é
     * limpa, como o CHECK da tabela exige. Se já está ativa, 409.
     */
    public AutorizacaoContatoResponseDTO ativar(int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);

        if (autorizacao.estaAtiva()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A autorização já está ativa.");
        }
        autorizacao.ativar();
        autorizacaoDAO.alterar(autorizacao);
        return AutorizacaoContatoMapper.toDto(buscarExistente(idAutorizacao));
    }

    /**
     * Revoga a autorização: status REVOGADA e data de revogação preenchida. A linha permanece como
     * histórico. Se já foi revogada, 409.
     */
    public AutorizacaoContatoResponseDTO revogar(int idAutorizacao) {
        AutorizacaoContato autorizacao = buscarExistente(idAutorizacao);

        if (autorizacao.getStatusAutorizacao() == StatusAutorizacaoEnum.REVOGADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A autorização já foi revogada.");
        }
        autorizacao.revogar();
        autorizacaoDAO.alterar(autorizacao);
        return AutorizacaoContatoMapper.toDto(buscarExistente(idAutorizacao));
    }

    /** Busca a autorização ou lança "não encontrada". */
    private AutorizacaoContato buscarExistente(int idAutorizacao) {
        AutorizacaoContato autorizacao = autorizacaoDAO.buscarPorId(idAutorizacao);
        if (autorizacao == null) {
            throw new IllegalArgumentException("Autorização não encontrada");
        }
        return autorizacao;
    }

    // a autorização fica presa ao vínculo do morador com a residência
    private MoradorResidencia verificarVinculo(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);
        if (vinculo == null) {
            throw new IllegalArgumentException("Vínculo do morador com a residência não encontrado");
        }
        return vinculo;
    }
}