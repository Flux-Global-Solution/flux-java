package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.StatusContaEnum;
import br.com.fiap.flux.models.Morador;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de {@link Morador} na
 * tabela {@code Morador}.
 *
 * <p>A senha é armazenada já como hash SHA-256 ({@code ds_senha_hash}); o DAO apenas grava e lê o
 * valor recebido, sem gerar o hash.</p>
 */
public class MoradorDAO {

    /**
     * Cadastra um novo morador. O id é gerado pelo banco (IDENTITY) e preenchido no próprio objeto;
     * a data de cadastro usa o padrão da tabela (SYSDATE).
     *
     * @param morador morador a ser cadastrado
     */
    public void cadastrar(Morador morador) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "INSERT INTO Morador (nm_morador, ds_email, ds_senha_hash, " +
                    "ds_telefone, ds_status_conta) " +
                    "VALUES (?, ?, ?, ?, ?)";

            ps = conexao.prepareStatement(sql, new String[]{"ID_MORADOR"});
            ps.setString(1, morador.getNome());
            ps.setString(2, morador.getEmail());
            ps.setString(3, morador.getHashSenha());
            ps.setString(4, morador.getTelefone());
            ps.setString(5, morador.getStatusConta().toString());

            ps.executeUpdate();

            // Recupera o id_morador gerado automaticamente (IDENTITY) e preenche no objeto
            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    morador.setIdMorador(rsGerado.getInt(1));
                }
            }
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
     * Atualiza os dados pessoais de um morador já cadastrado (nome, e-mail e telefone). A senha e o
     * status da conta não são alterados aqui.
     *
     * @param morador morador com os dados atualizados (o id é usado como referência)
     */
    public void alterar(Morador morador) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "UPDATE Morador SET nm_morador = ?, ds_email = ?, ds_telefone = ? " +
                    "WHERE id_morador = ?";

            ps = conexao.prepareStatement(sql);
            ps.setString(1, morador.getNome());
            ps.setString(2, morador.getEmail());
            ps.setString(3, morador.getTelefone());
            ps.setInt(4, morador.getIdMorador());

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
     * Grava o novo hash de senha do morador.
     *
     * @param idMorador identificador do morador
     * @param hashSenha novo hash SHA-256 da senha
     */
    public void alterarSenha(int idMorador, String hashSenha) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("UPDATE Morador SET ds_senha_hash = ? WHERE id_morador = ?");
            ps.setString(1, hashSenha);
            ps.setInt(2, idMorador);

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
     * Grava o novo status da conta do morador. É usado na desativação da conta.
     *
     * @param idMorador identificador do morador
     * @param status    novo status da conta
     */
    public void alterarStatus(int idMorador, StatusContaEnum status) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("UPDATE Morador SET ds_status_conta = ? WHERE id_morador = ?");
            ps.setString(1, status.name());
            ps.setInt(2, idMorador);

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
     * Remove um morador do banco de dados.
     *
     * @param idMorador identificador do morador a ser removido
     */
    public void excluir(int idMorador) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("DELETE FROM Morador WHERE id_morador = ?");
            ps.setInt(1, idMorador);
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
     * Busca um morador pelo seu identificador.
     *
     * @param idMorador identificador do morador
     * @return o morador encontrado, ou {@code null} caso não exista
     */
    public Morador buscarPorId(int idMorador) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        Morador morador = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Morador WHERE id_morador = ?");
            ps.setInt(1, idMorador);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                morador = lerMorador(rs);
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
        return morador;
    }

    /**
     * Busca um morador pelo e-mail, sem diferenciar maiúsculas de minúsculas.
     *
     * @param email e-mail do morador
     * @return o morador encontrado, ou {@code null} caso não exista
     */
    public Morador buscarPorEmail(String email) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        Morador morador = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Morador WHERE LOWER(ds_email) = LOWER(?) " +
                    "ORDER BY id_morador FETCH FIRST 1 ROWS ONLY");
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                morador = lerMorador(rs);
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
        return morador;
    }

    /**
     * Lista todos os moradores cadastrados, em uma única consulta.
     *
     * @return lista de moradores cadastrados
     */
    public List<Morador> listar() {
        List<Morador> moradores = new ArrayList<>();
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Morador");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                moradores.add(lerMorador(rs));
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
        return moradores;
    }

    /**
     * Monta o morador a partir da linha atual do resultado, lendo as colunas pelo nome e não pela
     * posição.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return o morador montado
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Morador lerMorador(ResultSet rs) throws SQLException {
        Morador morador = new Morador();
        morador.setIdMorador(rs.getInt("id_morador"));
        morador.setNome(rs.getString("nm_morador"));
        morador.setEmail(rs.getString("ds_email"));
        morador.setHashSenha(rs.getString("ds_senha_hash"));
        morador.setTelefone(rs.getString("ds_telefone"));
        morador.setStatusConta(StatusContaEnum.valueOf(rs.getString("ds_status_conta")));
        morador.setDataCadastro(lerData(rs.getDate("dt_cadastro")));
        return morador;
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