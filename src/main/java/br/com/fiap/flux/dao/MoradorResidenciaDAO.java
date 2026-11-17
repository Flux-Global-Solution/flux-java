package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.StatusContaEnum;
import br.com.fiap.flux.enums.TipoVinculoEnum;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de
 * {@link MoradorResidencia} na tabela {@code Morador_Residencia}.
 *
 * <p>O id é gerado pelo banco (IDENTITY) e a data do vínculo usa o padrão da tabela (SYSDATE). Os
 * relacionamentos com {@link Morador} e {@link Residencia} são gravados apenas pelas chaves
 * estrangeiras. Ao ler, a residência vem só com o id; o morador também, exceto em
 * {@link #listarPorResidencia(int)}, que traz os dados dele (nunca a senha).</p>
 */
public class MoradorResidenciaDAO {

    /**
     * Cadastra um novo vínculo entre morador e residência, com conexão e transação próprias. O id
     * gerado é preenchido no próprio objeto.
     *
     * @param moradorResidencia vínculo a ser cadastrado
     */
    public void cadastrar(MoradorResidencia moradorResidencia) {
        Connection conexao = ConnectionFactory.obterConexao();
        try {
            cadastrar(conexao, moradorResidencia);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Cadastra o vínculo usando uma conexão já aberta, para participar da transação de quem chama
     * (por exemplo, criar a residência e o titular juntos). Não faz commit nem rollback.
     *
     * @param con               conexão (e transação) do chamador
     * @param moradorResidencia vínculo a ser cadastrado
     * @throws SQLException se o INSERT falhar
     */
    public void cadastrar(Connection con, MoradorResidencia moradorResidencia) throws SQLException {
        String sql = "INSERT INTO Morador_Residencia (ds_tipo_vinculo, id_morador, id_residencia) " +
                "VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_MORADOR_RESIDENCIA"})) {
            ps.setString(1, moradorResidencia.getTipoVinculo().toString());
            ps.setInt(2, moradorResidencia.getMorador().getIdMorador());
            ps.setInt(3, moradorResidencia.getResidencia().getIdResidencia());
            ps.executeUpdate();

            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    moradorResidencia.setIdMoradorResidencia(rsGerado.getInt(1));
                }
            }
        }
    }

    /**
     * Atualiza os dados de um vínculo já cadastrado.
     *
     * @param moradorResidencia vínculo com os dados atualizados (o id é usado como referência)
     */
    public void alterar(MoradorResidencia moradorResidencia) {
        String sql = "UPDATE Morador_Residencia SET ds_tipo_vinculo = ?, dt_vinculo = ?, " +
                "id_morador = ?, id_residencia = ? " +
                "WHERE id_morador_residencia = ?";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setString(1, moradorResidencia.getTipoVinculo().toString());
            setData(ps, 2, moradorResidencia.getDataVinculo());
            ps.setInt(3, moradorResidencia.getMorador().getIdMorador());
            ps.setInt(4, moradorResidencia.getResidencia().getIdResidencia());
            ps.setInt(5, moradorResidencia.getIdMoradorResidencia());

            ps.executeUpdate();
            ps.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Remove um vínculo do banco de dados. Na mesma transação saem as autorizações de contatos de
     * confiança concedidas por esse vínculo, que dependem dele; os contatos em si permanecem. Ou
     * sai tudo, ou nada.
     *
     * @param idMoradorResidencia identificador do vínculo a ser removido
     */
    public void excluir(int idMoradorResidencia) {
        String[] comandos = {
                "DELETE FROM Autorizacao_Contato WHERE id_morador_residencia = ?",
                "DELETE FROM Morador_Residencia WHERE id_morador_residencia = ?"
        };

        Connection conexao = ConnectionFactory.obterConexao();

        try {
            boolean confirmado = false;
            try {
                conexao.setAutoCommit(false);
                for (String sql : comandos) {
                    try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                        ps.setInt(1, idMoradorResidencia);
                        ps.executeUpdate();
                    }
                }
                conexao.commit();
                confirmado = true;
            } finally {
                if (!confirmado) {
                    conexao.rollback();
                }
                conexao.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Busca um vínculo pelo seu identificador.
     *
     * @param idMoradorResidencia identificador do vínculo
     * @return o vínculo encontrado, ou {@code null} caso não exista
     */
    public MoradorResidencia buscarPorId(int idMoradorResidencia) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        MoradorResidencia moradorResidencia = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Morador_Residencia WHERE id_morador_residencia = ?");
            ps.setInt(1, idMoradorResidencia);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                moradorResidencia = montar(rs);
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return moradorResidencia;
    }

    /**
     * Busca o vínculo de um morador com uma residência. É por aqui que se confere se o morador tem
     * acesso à residência e se é titular.
     *
     * @param idMorador    identificador do morador
     * @param idResidencia identificador da residência
     * @return o vínculo, ou {@code null} se o morador não tem acesso à residência
     */
    public MoradorResidencia buscarVinculo(int idMorador, int idResidencia) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        MoradorResidencia moradorResidencia = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Morador_Residencia WHERE id_morador = ? AND id_residencia = ?");
            ps.setInt(1, idMorador);
            ps.setInt(2, idResidencia);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                moradorResidencia = montar(rs);
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return moradorResidencia;
    }

    /**
     * Busca o id do morador titular de uma residência. É usado pelas rotas que identificam a
     * residência somente pelo próprio ID.
     *
     * @param idResidencia identificador da residência
     * @return o id do morador titular, ou {@code 0} caso não encontre ou não tenha titular
     */
    public int buscarIdTitular(int idResidencia) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        int idMorador = 0;

        try {
            ps = conexao.prepareStatement(
                    "SELECT id_morador FROM Morador_Residencia " +
                            "WHERE id_residencia = ? AND ds_tipo_vinculo = 'TITULAR'");
            ps.setInt(1, idResidencia);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                idMorador = rs.getInt("id_morador");
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return idMorador;
    }

    /**
     * Lista os moradores vinculados a uma residência (titular primeiro), em uma única consulta.
     * Cada morador vem com nome, e-mail, telefone, status e data de cadastro; a senha não é lida.
     *
     * @param idResidencia identificador da residência
     * @return vínculos da residência
     */
    public List<MoradorResidencia> listarPorResidencia(int idResidencia) {
        List<MoradorResidencia> vinculos = new ArrayList<>();
        String sql = "SELECT mr.id_morador_residencia, mr.ds_tipo_vinculo, mr.dt_vinculo, " +
                "mr.id_morador, mr.id_residencia, " +
                "m.nm_morador, m.ds_email, m.ds_telefone, m.ds_status_conta, m.dt_cadastro " +
                "FROM Morador_Residencia mr " +
                "JOIN Morador m ON m.id_morador = mr.id_morador " +
                "WHERE mr.id_residencia = ? " +
                "ORDER BY CASE mr.ds_tipo_vinculo WHEN 'TITULAR' THEN 0 ELSE 1 END, m.nm_morador";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setInt(1, idResidencia);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                MoradorResidencia vinculo = montar(rs);
                Morador morador = vinculo.getMorador();
                morador.setNome(rs.getString("nm_morador"));
                morador.setEmail(rs.getString("ds_email"));
                morador.setTelefone(rs.getString("ds_telefone"));
                morador.setStatusConta(StatusContaEnum.valueOf(rs.getString("ds_status_conta")));
                morador.setDataCadastro(lerData(rs.getDate("dt_cadastro")));
                vinculos.add(vinculo);
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return vinculos;
    }

    /**
     * Lista todos os vínculos cadastrados, em uma única consulta.
     *
     * @return lista de vínculos cadastrados
     */
    public List<MoradorResidencia> listar() {
        List<MoradorResidencia> vinculos = new ArrayList<>();
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Morador_Residencia");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                vinculos.add(montar(rs));
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conexao != null && !conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return vinculos;
    }

    /**
     * Monta o vínculo a partir da linha atual do resultado. Morador e residência vêm só com o id.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return o vínculo montado
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private MoradorResidencia montar(ResultSet rs) throws SQLException {
        MoradorResidencia moradorResidencia = new MoradorResidencia();
        moradorResidencia.setIdMoradorResidencia(rs.getInt("id_morador_residencia"));
        moradorResidencia.setTipoVinculo(TipoVinculoEnum.valueOf(rs.getString("ds_tipo_vinculo")));
        moradorResidencia.setDataVinculo(lerData(rs.getDate("dt_vinculo")));

        Morador morador = new Morador();
        morador.setIdMorador(rs.getInt("id_morador"));
        moradorResidencia.setMorador(morador);

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(rs.getInt("id_residencia"));
        moradorResidencia.setResidencia(residencia);
        return moradorResidencia;
    }

    /**
     * Grava uma {@link LocalDate} (ou NULL) no parâmetro indicado.
     *
     * @param ps     statement a ser preenchido
     * @param indice posição do parâmetro
     * @param data   data a gravar, ou {@code null}
     * @throws SQLException se o parâmetro não puder ser gravado
     */
    private void setData(PreparedStatement ps, int indice, LocalDate data) throws SQLException {
        if (data != null) {
            ps.setDate(indice, Date.valueOf(data));
        } else {
            ps.setNull(indice, Types.DATE);
        }
    }

    /**
     * Converte um {@link Date} do JDBC em {@link LocalDate}, tratando NULL.
     *
     * @param data valor lido do banco, possivelmente {@code null}
     * @return a data, ou {@code null} se o valor for NULL
     */
    private LocalDate lerData(Date data) {
        return data != null ? data.toLocalDate() : null;
    }
}