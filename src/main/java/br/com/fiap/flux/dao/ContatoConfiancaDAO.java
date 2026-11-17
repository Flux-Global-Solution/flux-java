package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.TipoContatoEnum;
import br.com.fiap.flux.models.ContatoConfianca;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de
 * {@link ContatoConfianca} na tabela {@code Contato_Confianca}.
 *
 * <p>O id é gerado pelo banco (IDENTITY). A tabela não guarda quem cadastrou o contato: o "dono" é
 * quem tem autorizações dele (via {@code Morador_Residencia}), o que os métodos de contagem
 * permitem conferir.</p>
 */
public class ContatoConfiancaDAO {

    /**
     * Cadastra um novo contato. O id gerado é preenchido no próprio objeto.
     *
     * @param contato contato a ser cadastrado
     */
    public void cadastrar(ContatoConfianca contato) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "INSERT INTO Contato_Confianca (nm_contato, ds_telefone, ds_email, ds_tipo_contato) " +
                    "VALUES (?, ?, ?, ?)";

            ps = conexao.prepareStatement(sql, new String[]{"ID_CONTATO"});
            ps.setString(1, contato.getNome());
            ps.setString(2, contato.getTelefone());
            ps.setString(3, contato.getEmail());
            ps.setString(4, contato.getTipoContato().toString());

            ps.executeUpdate();

            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    contato.setIdContato(rsGerado.getInt(1));
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
     * Atualiza os dados de um contato já cadastrado.
     *
     * @param contato contato com os dados atualizados (o id é usado como referência)
     */
    public void alterar(ContatoConfianca contato) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "UPDATE Contato_Confianca SET nm_contato = ?, ds_telefone = ?, ds_email = ?, " +
                    "ds_tipo_contato = ? WHERE id_contato = ?";

            ps = conexao.prepareStatement(sql);
            ps.setString(1, contato.getNome());
            ps.setString(2, contato.getTelefone());
            ps.setString(3, contato.getEmail());
            ps.setString(4, contato.getTipoContato().toString());
            ps.setInt(5, contato.getIdContato());

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
     * Remove o contato e, na mesma transação, as autorizações {@code PENDENTE} dele, que nunca
     * foram concedidas e não deixam histórico. Autorizações ativas ou revogadas e notificações são
     * histórico: se existirem, a chave estrangeira barra a exclusão e nada é apagado.
     *
     * @param idContato identificador do contato a ser removido
     */
    public void excluirComAutorizacoesPendentes(int idContato) {
        String[] comandos = {
                "DELETE FROM Autorizacao_Contato WHERE id_contato = ? AND ds_status_autorizacao = 'PENDENTE'",
                "DELETE FROM Contato_Confianca WHERE id_contato = ?"
        };

        Connection conexao = ConnectionFactory.obterConexao();

        try {
            boolean confirmado = false;
            try {
                conexao.setAutoCommit(false);
                for (String sql : comandos) {
                    try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                        ps.setInt(1, idContato);
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
     * Busca um contato pelo seu identificador.
     *
     * @param idContato identificador do contato
     * @return o contato encontrado, ou {@code null} caso não exista
     */
    public ContatoConfianca buscarPorId(int idContato) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        ContatoConfianca contato = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Contato_Confianca WHERE id_contato = ?");
            ps.setInt(1, idContato);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                contato = montar(rs);
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
        return contato;
    }

    /**
     * Lista os contatos de um morador, isto é, os que têm autorização em algum vínculo dele, em uma
     * única consulta.
     *
     * @param idMorador identificador do morador
     * @return contatos do morador
     */
    public List<ContatoConfianca> listarPorMorador(int idMorador) {
        List<ContatoConfianca> contatos = new ArrayList<>();
        String sql = "SELECT DISTINCT c.* FROM Contato_Confianca c " +
                "JOIN Autorizacao_Contato ac ON ac.id_contato = c.id_contato " +
                "JOIN Morador_Residencia mr ON mr.id_morador_residencia = ac.id_morador_residencia " +
                "WHERE mr.id_morador = ? ORDER BY c.id_contato";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setInt(1, idMorador);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                contatos.add(montar(rs));
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
        return contatos;
    }

    /**
     * Lista todos os contatos cadastrados, em uma única consulta.
     *
     * @return lista de contatos cadastrados
     */
    public List<ContatoConfianca> listar() {
        List<ContatoConfianca> contatos = new ArrayList<>();
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Contato_Confianca");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                contatos.add(montar(rs));
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
        return contatos;
    }

    /**
     * Conta as autorizações do contato, de qualquer status e de qualquer residência.
     *
     * @param idContato identificador do contato
     * @return total de autorizações do contato
     */
    public int contarAutorizacoes(int idContato) {
        return contar("SELECT COUNT(*) FROM Autorizacao_Contato WHERE id_contato = ?", idContato, null);
    }

    /**
     * Conta as autorizações do contato que estão em vínculos do morador informado.
     *
     * @param idContato identificador do contato
     * @param idMorador identificador do morador
     * @return total de autorizações do contato nos vínculos do morador
     */
    public int contarAutorizacoesDoMorador(int idContato, int idMorador) {
        return contar("SELECT COUNT(*) FROM Autorizacao_Contato ac " +
                "JOIN Morador_Residencia mr ON mr.id_morador_residencia = ac.id_morador_residencia " +
                "WHERE ac.id_contato = ? AND mr.id_morador = ?", idContato, idMorador);
    }

    /**
     * Conta as autorizações do contato já concedidas ({@code ATIVA}) ou revogadas, que são
     * histórico e não podem ser apagadas.
     *
     * @param idContato identificador do contato
     * @return total de autorizações que não estão pendentes
     */
    public int contarAutorizacoesNaoPendentes(int idContato) {
        return contar("SELECT COUNT(*) FROM Autorizacao_Contato WHERE id_contato = ? " +
                "AND ds_status_autorizacao <> 'PENDENTE'", idContato, null);
    }

    /**
     * Conta as notificações já registradas para o contato.
     *
     * @param idContato identificador do contato
     * @return total de notificações do contato
     */
    public int contarNotificacoes(int idContato) {
        return contar("SELECT COUNT(*) FROM Notificacao WHERE id_contato = ?", idContato, null);
    }

    /**
     * Executa uma consulta {@code COUNT} com um ou dois parâmetros inteiros.
     *
     * @param sql      consulta que devolve a contagem na primeira coluna
     * @param primeiro valor do primeiro parâmetro
     * @param segundo  valor do segundo parâmetro, ou {@code null} se a consulta tem só um
     * @return o resultado da contagem
     */
    private int contar(String sql, int primeiro, Integer segundo) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        int total = 0;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setInt(1, primeiro);
            if (segundo != null) {
                ps.setInt(2, segundo);
            }

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                total = rs.getInt(1);
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
        return total;
    }

    /**
     * Monta o contato a partir da linha atual do resultado, lendo as colunas pelo nome.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return o contato montado
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private ContatoConfianca montar(ResultSet rs) throws SQLException {
        ContatoConfianca contato = new ContatoConfianca();
        contato.setIdContato(rs.getInt("id_contato"));
        contato.setNome(rs.getString("nm_contato"));
        contato.setTelefone(rs.getString("ds_telefone"));
        contato.setEmail(rs.getString("ds_email"));
        contato.setTipoContato(TipoContatoEnum.valueOf(rs.getString("ds_tipo_contato")));
        return contato;
    }
}