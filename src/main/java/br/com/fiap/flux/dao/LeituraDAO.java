package br.com.fiap.flux.dao;

import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Leitura;

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
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de {@link Leitura} na
 * tabela {@code Leitura}.
 *
 * <p>O id é gerado pelo banco (IDENTITY). A associação com {@link Dispositivo} é gravada pela chave
 * estrangeira {@code id_dispositivo}; ao ler, o dispositivo vem só com o id. Data e hora da leitura
 * são guardadas completas, e a data de sincronização é opcional (NULL enquanto não sincronizada).
 * Toda listagem sai em ordem cronológica: data da leitura e, no empate, número de sequência.</p>
 */
public class LeituraDAO {

    private static final int ERRO_CHAVE_DUPLICADA = 1;

    private static final String SQL_INSERIR = "INSERT INTO Leitura (dt_leitura, nr_sequencia, " +
            "vl_vazao_litro_minuto, vl_volume_litros, nr_duracao_fluxo_seg, nr_sincronizada, " +
            "dt_sincronizacao, id_dispositivo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Cadastra uma nova leitura. O id gerado é preenchido no próprio objeto.
     *
     * @param leitura leitura a ser cadastrada
     * @throws IllegalArgumentException se a sequência já existe para o dispositivo
     * @throws IllegalStateException    se o banco falhar
     */
    public void cadastrar(Leitura leitura) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(SQL_INSERIR, new String[]{"ID_LEITURA"})) {
            inserir(ps, leitura);
        } catch (SQLException e) {
            e.printStackTrace();
            throw erroDeGravacao(e, "Não foi possível cadastrar a leitura.");
        }
    }

    /**
     * Cadastra várias leituras em uma única transação: ou todas são gravadas, ou nenhuma. O id
     * gerado de cada uma é preenchido no próprio objeto.
     *
     * @param leituras leituras a serem cadastradas
     * @throws IllegalArgumentException se alguma sequência já existe para o dispositivo (nada é
     *     gravado)
     * @throws IllegalStateException    se o banco falhar (nada é gravado)
     */
    public void cadastrarLote(List<Leitura> leituras) {
        try (Connection con = ConnectionFactory.obterConexao()) {
            boolean confirmado = false;
            try {
                con.setAutoCommit(false);
                try (PreparedStatement ps = con.prepareStatement(SQL_INSERIR, new String[]{"ID_LEITURA"})) {
                    for (Leitura leitura : leituras) {
                        inserir(ps, leitura);
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
            leituras.forEach(l -> l.setIdLeitura(0));
            throw erroDeGravacao(e, "Não foi possível cadastrar as leituras.");
        }
    }

    /**
     * Atualiza os dados de uma leitura já cadastrada.
     *
     * @param leitura leitura com os dados atualizados (o id é usado como referência)
     * @throws IllegalStateException se o banco falhar
     */
    public void alterar(Leitura leitura) {
        String sql = "UPDATE Leitura SET dt_leitura = ?, nr_sequencia = ?, vl_vazao_litro_minuto = ?, " +
                "vl_volume_litros = ?, nr_duracao_fluxo_seg = ?, nr_sincronizada = ?, " +
                "dt_sincronizacao = ?, id_dispositivo = ? WHERE id_leitura = ?";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setDataHora(ps, 1, leitura.getDataLeitura());
            ps.setInt(2, leitura.getSequencia());
            ps.setDouble(3, leitura.getVazaoLitroMinuto());
            ps.setDouble(4, leitura.getVolumeLitros());
            ps.setInt(5, leitura.getDuracaoFluxoSeg());
            ps.setInt(6, leitura.getSincronizada());
            setDataHora(ps, 7, leitura.getDataSincronizacao());
            ps.setInt(8, leitura.getDispositivo().getIdDispositivo());
            ps.setInt(9, leitura.getIdLeitura());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível alterar a leitura.", e);
        }
    }

    /**
     * Remove uma leitura do banco de dados.
     *
     * @param idLeitura identificador da leitura a ser removida
     * @throws IllegalStateException se o banco falhar
     */
    public void excluir(int idLeitura) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("DELETE FROM Leitura WHERE id_leitura = ?")) {
            ps.setInt(1, idLeitura);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível excluir a leitura.", e);
        }
    }

    /**
     * Busca uma leitura pelo seu identificador.
     *
     * @param idLeitura identificador da leitura
     * @return a leitura encontrada, ou {@code null} caso não exista
     * @throws IllegalStateException se o banco falhar
     */
    public Leitura buscarPorId(int idLeitura) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM Leitura WHERE id_leitura = ?")) {
            ps.setInt(1, idLeitura);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? montar(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível consultar a leitura.", e);
        }
    }

    /**
     * Busca a leitura de um dispositivo com determinada sequência. Serve para impedir sequência
     * repetida no mesmo dispositivo.
     *
     * @param idDispositivo identificador do dispositivo
     * @param sequencia     número de sequência da leitura
     * @return a leitura encontrada, ou {@code null} se a sequência ainda não foi usada
     * @throws IllegalStateException se o banco falhar
     */
    public Leitura buscarPorDispositivoESequencia(int idDispositivo, int sequencia) {
        String sql = "SELECT * FROM Leitura WHERE id_dispositivo = ? AND nr_sequencia = ?";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idDispositivo);
            ps.setInt(2, sequencia);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? montar(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível consultar a leitura.", e);
        }
    }

    /**
     * Lista leituras em ordem cronológica (data e, no empate, sequência), combinando os filtros
     * informados. Todo filtro é opcional ({@code null} não filtra); o período é inclusivo nos dois
     * extremos.
     *
     * @param idResidencia  só leituras de dispositivos desta residência
     * @param idDispositivo só leituras deste dispositivo
     * @param idMorador     só leituras de residências às quais este morador tem vínculo
     * @param de            início do período
     * @param ate           fim do período
     * @return leituras que atendem aos filtros
     * @throws IllegalStateException se o banco falhar
     */
    public List<Leitura> listarFiltrado(Integer idResidencia, Integer idDispositivo, Integer idMorador,
                                        LocalDateTime de, LocalDateTime ate) {
        List<Leitura> leituras = new ArrayList<>();
        String sql = "SELECT l.* FROM Leitura l " +
                "JOIN Dispositivo d ON d.id_dispositivo = l.id_dispositivo WHERE 1 = 1 " +
                (idResidencia != null ? "AND d.id_residencia = ? " : "") +
                (idDispositivo != null ? "AND l.id_dispositivo = ? " : "") +
                (idMorador != null ? "AND EXISTS (SELECT 1 FROM Morador_Residencia mr " +
                        "WHERE mr.id_residencia = d.id_residencia AND mr.id_morador = ?) " : "") +
                (de != null ? "AND l.dt_leitura >= ? " : "") +
                (ate != null ? "AND l.dt_leitura <= ? " : "") +
                "ORDER BY l.dt_leitura, l.nr_sequencia, l.id_leitura";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int indice = 1;
            if (idResidencia != null) {
                ps.setInt(indice++, idResidencia);
            }
            if (idDispositivo != null) {
                ps.setInt(indice++, idDispositivo);
            }
            if (idMorador != null) {
                ps.setInt(indice++, idMorador);
            }
            if (de != null) {
                ps.setTimestamp(indice++, Timestamp.valueOf(de));
            }
            if (ate != null) {
                ps.setTimestamp(indice, Timestamp.valueOf(ate));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    leituras.add(montar(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível listar as leituras.", e);
        }
        return leituras;
    }

    /**
     * Lista todas as leituras cadastradas, em uma única consulta.
     *
     * @return lista de leituras cadastradas
     * @throws IllegalStateException se o banco falhar
     */
    public List<Leitura> listar() {
        List<Leitura> leituras = new ArrayList<>();

        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM Leitura ORDER BY dt_leitura, nr_sequencia, id_leitura");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                leituras.add(montar(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível listar as leituras.", e);
        }
        return leituras;
    }

    /**
     * Grava a leitura no statement de inserção, executa o INSERT e preenche o id gerado.
     *
     * @param ps      statement de inserção já preparado
     * @param leitura leitura a ser gravada
     * @throws SQLException se a gravação falhar
     */
    private void inserir(PreparedStatement ps, Leitura leitura) throws SQLException {
        setDataHora(ps, 1, leitura.getDataLeitura());
        ps.setInt(2, leitura.getSequencia());
        ps.setDouble(3, leitura.getVazaoLitroMinuto());
        ps.setDouble(4, leitura.getVolumeLitros());
        ps.setInt(5, leitura.getDuracaoFluxoSeg());
        ps.setInt(6, leitura.getSincronizada());
        setDataHora(ps, 7, leitura.getDataSincronizacao());
        ps.setInt(8, leitura.getDispositivo().getIdDispositivo());
        ps.executeUpdate();

        try (ResultSet rsGerado = ps.getGeneratedKeys()) {
            if (rsGerado.next()) {
                leitura.setIdLeitura(rsGerado.getInt(1));
            }
        }
    }

    /**
     * Converte uma falha de gravação na exceção adequada: sequência repetida no dispositivo vira
     * erro de validação; qualquer outra falha é interna.
     *
     * @param e        falha do banco
     * @param mensagem mensagem usada quando a falha não é de sequência repetida
     * @return {@link IllegalArgumentException} para sequência repetida, ou
     *     {@link IllegalStateException} nos demais casos
     */
    private RuntimeException erroDeGravacao(SQLException e, String mensagem) {
        if (e.getErrorCode() == ERRO_CHAVE_DUPLICADA) {
            return new IllegalArgumentException("Já existe uma leitura com esta sequência para o dispositivo.", e);
        }
        return new IllegalStateException(mensagem, e);
    }

    /**
     * Monta a leitura a partir da linha atual do resultado, lendo as colunas pelo nome.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return a leitura montada
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Leitura montar(ResultSet rs) throws SQLException {
        Leitura leitura = new Leitura();
        leitura.setIdLeitura(rs.getInt("id_leitura"));
        leitura.setDataLeitura(lerDataHora(rs.getTimestamp("dt_leitura")));
        leitura.setSequencia(rs.getInt("nr_sequencia"));
        leitura.setVazaoLitroMinuto(rs.getDouble("vl_vazao_litro_minuto"));
        leitura.setVolumeLitros(rs.getDouble("vl_volume_litros"));
        leitura.setDuracaoFluxoSeg(rs.getInt("nr_duracao_fluxo_seg"));
        leitura.setSincronizada(rs.getInt("nr_sincronizada"));
        leitura.setDataSincronizacao(lerDataHora(rs.getTimestamp("dt_sincronizacao")));

        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setIdDispositivo(rs.getInt("id_dispositivo"));
        leitura.setDispositivo(dispositivo);
        return leitura;
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