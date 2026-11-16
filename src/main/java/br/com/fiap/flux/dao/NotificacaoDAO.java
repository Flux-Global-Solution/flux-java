package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.CanalEnum;
import br.com.fiap.flux.enums.StatusEnvioEnum;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.ContatoConfianca;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.Notificacao;

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
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de {@link Notificacao}
 * na tabela {@code Notificacao}.
 *
 * <p>O id é gerado pelo banco (IDENTITY). As associações com {@link Alerta}, {@link Morador} e
 * {@link ContatoConfianca} são gravadas pelas chaves estrangeiras; ao ler, esses objetos vêm só com
 * o id. O destinatário é um morador ou um contato, e o banco exige exatamente um dos dois. A data
 * de envio é opcional (NULL enquanto a notificação não foi enviada).</p>
 */
public class NotificacaoDAO {

    private static final String SQL_INSERIR = "INSERT INTO Notificacao (ds_notificacao, dt_envio, ds_canal, " +
            "ds_status_envio, id_alerta, id_morador, id_contato) VALUES (?, ?, ?, ?, ?, ?, ?)";

    /**
     * Cadastra uma nova notificação. O id gerado é preenchido no próprio objeto.
     *
     * @param notificacao notificação a ser cadastrada
     * @throws IllegalStateException se o banco falhar
     */
    public void cadastrar(Notificacao notificacao) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(SQL_INSERIR, new String[]{"ID_NOTIFICACAO"})) {
            inserir(ps, notificacao);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível cadastrar a notificação.", e);
        }
    }

    /**
     * Cadastra várias notificações em uma única transação: ou todas são gravadas, ou nenhuma. O id
     * gerado de cada uma é preenchido no próprio objeto.
     *
     * @param notificacoes notificações a serem cadastradas
     * @throws IllegalStateException se o banco falhar (nada é gravado)
     */
    public void cadastrarLote(List<Notificacao> notificacoes) {
        try (Connection con = ConnectionFactory.obterConexao()) {
            boolean confirmado = false;
            try {
                con.setAutoCommit(false);
                try (PreparedStatement ps = con.prepareStatement(SQL_INSERIR, new String[]{"ID_NOTIFICACAO"})) {
                    for (Notificacao notificacao : notificacoes) {
                        inserir(ps, notificacao);
                    }
                }
                con.commit();
                confirmado = true;
            } finally {
                // Desfaz antes de devolver a conexão: no Oracle, ligar o autocommit com a
                // transação aberta confirmaria tudo.
                if (!confirmado) {
                    con.rollback();
                }
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            notificacoes.forEach(n -> n.setIdNotificacao(0));
            throw new IllegalStateException("Não foi possível cadastrar as notificações.", e);
        }
    }

    /**
     * Atualiza os dados de uma notificação já cadastrada.
     *
     * @param notificacao notificação com os dados atualizados (o id é usado como referência)
     * @throws IllegalStateException se o banco falhar
     */
    public void alterar(Notificacao notificacao) {
        String sql = "UPDATE Notificacao SET ds_notificacao = ?, dt_envio = ?, ds_canal = ?, " +
                "ds_status_envio = ?, id_alerta = ?, id_morador = ?, id_contato = ? " +
                "WHERE id_notificacao = ?";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            preencher(ps, notificacao);
            ps.setInt(8, notificacao.getIdNotificacao());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível alterar a notificação.", e);
        }
    }

    /**
     * Remove uma notificação do banco de dados.
     *
     * @param idNotificacao identificador da notificação a ser removida
     * @throws IllegalStateException se o banco falhar
     */
    public void excluir(int idNotificacao) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("DELETE FROM Notificacao WHERE id_notificacao = ?")) {
            ps.setInt(1, idNotificacao);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível excluir a notificação.", e);
        }
    }

    /**
     * Busca uma notificação pelo seu identificador.
     *
     * @param idNotificacao identificador da notificação
     * @return a notificação encontrada, ou {@code null} caso não exista
     * @throws IllegalStateException se o banco falhar
     */
    public Notificacao buscarPorId(int idNotificacao) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM Notificacao WHERE id_notificacao = ?")) {
            ps.setInt(1, idNotificacao);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? montar(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível consultar a notificação.", e);
        }
    }

    /**
     * Lista as notificações de um alerta, da mais antiga para a mais nova (ordem de criação).
     *
     * @param idAlerta identificador do alerta
     * @return notificações do alerta
     * @throws IllegalStateException se o banco falhar
     */
    public List<Notificacao> listarPorAlerta(int idAlerta) {
        List<Notificacao> notificacoes = new ArrayList<>();
        String sql = "SELECT * FROM Notificacao WHERE id_alerta = ? ORDER BY id_notificacao";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idAlerta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notificacoes.add(montar(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível listar as notificações do alerta.", e);
        }
        return notificacoes;
    }

    /**
     * Lista as notificações que o morador pode ver, da mais nova para a mais antiga: as endereçadas
     * a ele e, nas residências em que é titular, todas as dos alertas da residência, inclusive as
     * dos contatos. Com {@code status}, traz só as daquele status de envio.
     *
     * @param idMorador identificador do morador que consulta
     * @param status    status de envio para filtrar, ou {@code null} para trazer todas
     * @return notificações visíveis ao morador
     * @throws IllegalStateException se o banco falhar
     */
    public List<Notificacao> listarVisiveis(int idMorador, StatusEnvioEnum status) {
        List<Notificacao> notificacoes = new ArrayList<>();
        String sql = "SELECT n.* FROM Notificacao n JOIN Alerta a ON a.id_alerta = n.id_alerta " +
                "WHERE (n.id_morador = ? OR EXISTS (SELECT 1 FROM Morador_Residencia mr " +
                "WHERE mr.id_residencia = a.id_residencia AND mr.id_morador = ? " +
                "AND mr.ds_tipo_vinculo = 'TITULAR')) " +
                (status != null ? "AND n.ds_status_envio = ? " : "") +
                "ORDER BY n.id_notificacao DESC";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idMorador);
            ps.setInt(2, idMorador);
            if (status != null) {
                ps.setString(3, status.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notificacoes.add(montar(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível listar as notificações.", e);
        }
        return notificacoes;
    }

    /**
     * Lista todas as notificações cadastradas, em uma única consulta.
     *
     * @return lista de notificações cadastradas
     * @throws IllegalStateException se o banco falhar
     */
    public List<Notificacao> listar() {
        List<Notificacao> notificacoes = new ArrayList<>();

        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM Notificacao ORDER BY id_notificacao");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                notificacoes.add(montar(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível listar as notificações.", e);
        }
        return notificacoes;
    }

    /**
     * Grava a notificação no statement de inserção, executa o INSERT e preenche o id gerado.
     *
     * @param ps          statement de inserção já preparado
     * @param notificacao notificação a ser gravada
     * @throws SQLException se a gravação falhar
     */
    private void inserir(PreparedStatement ps, Notificacao notificacao) throws SQLException {
        ps.setString(1, notificacao.getNotificacao());
        setData(ps, 2, notificacao.getDataEnvio());
        ps.setString(3, notificacao.getCanal().name());
        ps.setString(4, notificacao.getStatusEnvio().name());
        ps.setInt(5, notificacao.getAlerta().getIdAlerta());
        setIdOpcional(ps, 6, notificacao.getMorador() != null ? notificacao.getMorador().getIdMorador() : null);
        setIdOpcional(ps, 7, notificacao.getContato() != null ? notificacao.getContato().getIdContato() : null);
        ps.executeUpdate();

        try (ResultSet rsGerado = ps.getGeneratedKeys()) {
            if (rsGerado.next()) {
                notificacao.setIdNotificacao(rsGerado.getInt(1));
            }
        }
    }

    /**
     * Grava os campos da notificação nos parâmetros 1 a 7 do statement (mesma ordem no INSERT e no
     * UPDATE).
     *
     * @param ps          statement a ser preenchido
     * @param notificacao notificação com os dados a gravar
     * @throws SQLException se algum parâmetro não puder ser gravado
     */
    private void preencher(PreparedStatement ps, Notificacao notificacao) throws SQLException {
        ps.setString(1, notificacao.getNotificacao());
        setData(ps, 2, notificacao.getDataEnvio());
        ps.setString(3, notificacao.getCanal().name());
        ps.setString(4, notificacao.getStatusEnvio().name());
        ps.setInt(5, notificacao.getAlerta().getIdAlerta());
        setIdOpcional(ps, 6, notificacao.getMorador() != null ? notificacao.getMorador().getIdMorador() : null);
        setIdOpcional(ps, 7, notificacao.getContato() != null ? notificacao.getContato().getIdContato() : null);
    }

    /**
     * Monta a notificação a partir da linha atual do resultado, lendo as colunas pelo nome.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return a notificação montada
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Notificacao montar(ResultSet rs) throws SQLException {
        Notificacao notificacao = new Notificacao();
        notificacao.setIdNotificacao(rs.getInt("id_notificacao"));
        notificacao.setNotificacao(rs.getString("ds_notificacao"));
        notificacao.setDataEnvio(lerData(rs.getDate("dt_envio")));
        notificacao.setCanal(CanalEnum.valueOf(rs.getString("ds_canal")));
        notificacao.setStatusEnvio(StatusEnvioEnum.valueOf(rs.getString("ds_status_envio")));

        Alerta alerta = new Alerta();
        alerta.setIdAlerta(rs.getInt("id_alerta"));
        notificacao.setAlerta(alerta);

        int idMorador = rs.getInt("id_morador");
        if (!rs.wasNull()) {
            Morador morador = new Morador();
            morador.setIdMorador(idMorador);
            notificacao.setMorador(morador);
        }
        int idContato = rs.getInt("id_contato");
        if (!rs.wasNull()) {
            ContatoConfianca contato = new ContatoConfianca();
            contato.setIdContato(idContato);
            notificacao.setContato(contato);
        }
        return notificacao;
    }

    /**
     * Grava um id (ou NULL) no parâmetro indicado.
     *
     * @param ps     statement a ser preenchido
     * @param indice posição do parâmetro
     * @param id     identificador a gravar, ou {@code null}
     * @throws SQLException se o parâmetro não puder ser gravado
     */
    private void setIdOpcional(PreparedStatement ps, int indice, Integer id) throws SQLException {
        if (id != null) {
            ps.setInt(indice, id);
        } else {
            ps.setNull(indice, Types.INTEGER);
        }
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