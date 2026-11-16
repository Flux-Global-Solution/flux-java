package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.StatusAutorizacaoEnum;
import br.com.fiap.flux.enums.TipoContatoEnum;
import br.com.fiap.flux.enums.TipoVinculoEnum;
import br.com.fiap.flux.models.AutorizacaoContato;
import br.com.fiap.flux.models.ContatoConfianca;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de
 * {@link AutorizacaoContato} na tabela {@code Autorizacao_Contato}.
 *
 * <p>O id é gerado pelo banco (IDENTITY). A autorização liga um contato a um vínculo
 * ({@code Morador_Residencia}); ao ler, o contato vem completo e o vínculo vem com o id, o tipo, o
 * morador e a residência (só os ids). A autorização nunca é apagada aqui: revogar é mudar o status
 * e preencher {@code dt_revogacao}, e o histórico permanece.</p>
 */
public class AutorizacaoContatoDAO {

    private static final int ORA_CHAVE_UNICA = 1;

    private static final String SELECT_BASE =
            "SELECT ac.id_autorizacao, ac.dt_autorizacao, ac.dt_revogacao, ac.ds_status_autorizacao, " +
                    "ac.id_contato, ac.id_morador_residencia, " +
                    "c.nm_contato, c.nr_telefone, c.ds_email, c.ds_tipo_contato, " +
                    "mr.id_residencia, mr.id_morador, mr.ds_tipo_vinculo " +
                    "FROM Autorizacao_Contato ac " +
                    "JOIN Contato_Confianca c ON c.id_contato = ac.id_contato " +
                    "JOIN Morador_Residencia mr ON mr.id_morador_residencia = ac.id_morador_residencia ";

    /**
     * Cadastra uma nova autorização. O id gerado é preenchido no próprio objeto.
     *
     * @param autorizacao autorização a ser cadastrada (contato e vínculo só com o id)
     * @throws IllegalArgumentException se o contato já tem autorização para esse vínculo
     * @throws IllegalStateException    se o banco falhar
     */
    public void cadastrar(AutorizacaoContato autorizacao) {
        String sql = "INSERT INTO Autorizacao_Contato (dt_autorizacao, dt_revogacao, " +
                "ds_status_autorizacao, id_contato, id_morador_residencia) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_AUTORIZACAO"})) {
            setDataHora(ps, 1, autorizacao.getDataAutorizacao());
            setDataHora(ps, 2, autorizacao.getDataRevogacao());
            ps.setString(3, autorizacao.getStatusAutorizacao().toString());
            ps.setInt(4, autorizacao.getContato().getIdContato());
            ps.setInt(5, autorizacao.getMoradorResidencia().getIdMoradorResidencia());
            ps.executeUpdate();

            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    autorizacao.setIdAutorizacao(rsGerado.getInt(1));
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == ORA_CHAVE_UNICA) {
                throw new IllegalArgumentException("Autorização já cadastrada para este contato nesta residência.");
            }
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível cadastrar a autorização.", e);
        }
    }

    /**
     * Grava o status e as duas datas da autorização. Serve tanto para ativar quanto para revogar;
     * contato e vínculo não mudam.
     *
     * @param autorizacao autorização com status e datas já definidos (o id é usado como referência)
     * @throws IllegalStateException se o banco falhar
     */
    public void alterar(AutorizacaoContato autorizacao) {
        String sql = "UPDATE Autorizacao_Contato SET ds_status_autorizacao = ?, dt_autorizacao = ?, " +
                "dt_revogacao = ? WHERE id_autorizacao = ?";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, autorizacao.getStatusAutorizacao().toString());
            setDataHora(ps, 2, autorizacao.getDataAutorizacao());
            setDataHora(ps, 3, autorizacao.getDataRevogacao());
            ps.setInt(4, autorizacao.getIdAutorizacao());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível alterar a autorização.", e);
        }
    }

    /**
     * Busca uma autorização pelo seu identificador.
     *
     * @param idAutorizacao identificador da autorização
     * @return a autorização encontrada, ou {@code null} caso não exista
     * @throws IllegalStateException se o banco falhar
     */
    public AutorizacaoContato buscarPorId(int idAutorizacao) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(SELECT_BASE + "WHERE ac.id_autorizacao = ?")) {
            ps.setInt(1, idAutorizacao);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? montar(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível consultar a autorização.", e);
        }
    }

    /**
     * Lista as autorizações de uma residência, em uma única consulta. Com {@code status}, traz só
     * as daquele status: {@code PENDENTE} para as pendentes ou {@code ATIVA} para os contatos
     * autorizados a receber notificações.
     *
     * @param idResidencia identificador da residência
     * @param status       status para filtrar, ou {@code null} para trazer todas
     * @return autorizações da residência, da mais antiga para a mais nova
     * @throws IllegalStateException se o banco falhar
     */
    public List<AutorizacaoContato> listarPorResidencia(int idResidencia, StatusAutorizacaoEnum status) {
        List<AutorizacaoContato> autorizacoes = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE mr.id_residencia = ? " +
                (status != null ? "AND ac.ds_status_autorizacao = ? " : "") +
                "ORDER BY ac.id_autorizacao";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idResidencia);
            if (status != null) {
                ps.setString(2, status.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    autorizacoes.add(montar(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível listar as autorizações.", e);
        }
        return autorizacoes;
    }

    /**
     * Monta a autorização, com contato e vínculo, a partir da linha atual do resultado.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return a autorização montada
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private AutorizacaoContato montar(ResultSet rs) throws SQLException {
        ContatoConfianca contato = new ContatoConfianca();
        contato.setIdContato(rs.getInt("id_contato"));
        contato.setNome(rs.getString("nm_contato"));
        contato.setTelefone(rs.getString("nr_telefone"));
        contato.setEmail(rs.getString("ds_email"));
        contato.setTipoContato(TipoContatoEnum.valueOf(rs.getString("ds_tipo_contato")));

        Morador morador = new Morador();
        morador.setIdMorador(rs.getInt("id_morador"));
        Residencia residencia = new Residencia();
        residencia.setIdResidencia(rs.getInt("id_residencia"));
        MoradorResidencia vinculo = new MoradorResidencia();
        vinculo.setIdMoradorResidencia(rs.getInt("id_morador_residencia"));
        vinculo.setTipoVinculo(TipoVinculoEnum.valueOf(rs.getString("ds_tipo_vinculo")));
        vinculo.setMorador(morador);
        vinculo.setResidencia(residencia);

        AutorizacaoContato autorizacao = new AutorizacaoContato();
        autorizacao.setIdAutorizacao(rs.getInt("id_autorizacao"));
        autorizacao.setDataAutorizacao(lerDataHora(rs.getTimestamp("dt_autorizacao")));
        autorizacao.setDataRevogacao(lerDataHora(rs.getTimestamp("dt_revogacao")));
        autorizacao.setStatusAutorizacao(StatusAutorizacaoEnum.valueOf(rs.getString("ds_status_autorizacao")));
        autorizacao.setContato(contato);
        autorizacao.setMoradorResidencia(vinculo);
        return autorizacao;
    }

    /**
     * Grava uma {@link LocalDateTime} (ou NULL) no parâmetro indicado.
     *
     * @param ps       statement a ser preenchido
     * @param indice   posição do parâmetro
     * @param dataHora data e hora a gravar, ou {@code null}
     * @throws SQLException se o parâmetro não puder ser gravado
     */
    private void setDataHora(PreparedStatement ps, int indice, LocalDateTime dataHora) throws SQLException {
        if (dataHora != null) {
            ps.setTimestamp(indice, Timestamp.valueOf(dataHora));
        } else {
            ps.setNull(indice, Types.TIMESTAMP);
        }
    }

    /**
     * Converte um {@link Timestamp} do JDBC em {@link LocalDateTime}, tratando NULL.
     *
     * @param dataHora valor lido do banco, possivelmente {@code null}
     * @return a data e hora, ou {@code null} se o valor for NULL
     */
    private LocalDateTime lerDataHora(Timestamp dataHora) {
        return dataHora != null ? dataHora.toLocalDateTime() : null;
    }
}