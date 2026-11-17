package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.MoradorResidenciaResponseDTO;
import br.com.fiap.flux.dto.VinculoRequestDTO;
import br.com.fiap.flux.enums.TipoVinculoEnum;
import br.com.fiap.flux.mapper.MoradorResidenciaMapper;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Compartilhamento de acesso a uma residência: vincular e remover moradores. Não se confunde com
 * os contatos de confiança (pessoas sem conta, que só recebem avisos). Contas novas nunca são
 * criadas aqui: o morador precisa já existir.
 */
@Service
public class VinculoService {
    private final MoradorResidenciaDAO moradorResidenciaDAO;
    private final MoradorDAO moradorDAO;
    private final ResidenciaDAO residenciaDAO;

    public VinculoService() {
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
        this.moradorDAO = new MoradorDAO();
        this.residenciaDAO = new ResidenciaDAO();
    }

    /**
     * Vincula à residência o morador dono do e-mail informado, como morador comum (é o tipo
     * assumido quando {@code tipoVinculo} não vem). Recusa conta inexistente ou inativa, vínculo
     * repetido e um segundo titular (409): a residência tem um só, definido no cadastro.
     */
    public MoradorResidenciaResponseDTO vincular(int idResidencia, VinculoRequestDTO dto) {
        if (residenciaDAO.buscarPorId(idResidencia) == null) {
            throw new IllegalArgumentException("Residência não encontrada");
        }
        validar(dto);

        TipoVinculoEnum tipo = dto.tipoVinculo() != null ? dto.tipoVinculo() : TipoVinculoEnum.MORADOR_COMUM;
        if (tipo == TipoVinculoEnum.TITULAR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A residência já possui um titular.");
        }

        Morador morador = moradorDAO.buscarPorEmail(dto.emailMorador().trim().toLowerCase());
        if (morador == null || !morador.estaAtivo()) {
            throw new IllegalArgumentException("Morador não encontrado com este e-mail");
        }
        if (moradorResidenciaDAO.buscarVinculo(morador.getIdMorador(), idResidencia) != null) {
            throw new IllegalArgumentException("Vínculo já cadastrado para este morador nesta residência.");
        }

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(idResidencia);
        MoradorResidencia vinculo = new MoradorResidencia();
        vinculo.setTipoVinculo(tipo);
        vinculo.setMorador(morador);
        vinculo.setResidencia(residencia);
        moradorResidenciaDAO.cadastrar(vinculo);
        if (vinculo.getIdMoradorResidencia() == 0) {
            throw new IllegalStateException("Não foi possível gravar o vínculo.");
        }
        return consultar(vinculo.getIdMoradorResidencia());
    }

    public MoradorResidenciaResponseDTO buscarPorId(int idVinculo) {
        return consultar(idVinculo);
    }

    /** Remove um vínculo de morador comum; o vínculo do titular não sai por aqui (409). */
    public void remover(int idVinculo) {
        MoradorResidencia vinculo = buscarExistente(idVinculo);
        if (vinculo.isTitular()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O titular não pode ser removido da residência.");
        }
        moradorResidenciaDAO.excluir(idVinculo);
    }

    // ---------- auxiliares ----------

    // monta a resposta do vínculo com os dados do morador (sem senha)
    private MoradorResidenciaResponseDTO consultar(int idVinculo) {
        MoradorResidencia vinculo = buscarExistente(idVinculo);
        vinculo.setMorador(moradorDAO.buscarPorId(vinculo.getMorador().getIdMorador()));
        return MoradorResidenciaMapper.toDto(vinculo);
    }

    private MoradorResidencia buscarExistente(int idVinculo) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarPorId(idVinculo);
        if (vinculo == null) {
            throw new IllegalArgumentException("Vínculo não encontrado");
        }
        return vinculo;
    }

    private void validar(VinculoRequestDTO dto) {
        if (dto.emailMorador() == null || !dto.emailMorador().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
    }
}