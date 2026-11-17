package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Morador;
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
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de {@link Alerta} na
 * tabela {@code Alerta}.
 *
 * <p>O id é gerado pelo banco (IDENTITY). As associações com {@link Residencia},
 * {@link Dispositivo} e {@link Morador} são gravadas pelas chaves estrangeiras; ao ler, esses
 * objetos vêm só com o id. As datas guardam data e hora completas, e reconhecimento, resolução,
 * volume desperdiçado e morador são opcionais (NULL).</p>
 *
 * <p>O tipo {@code VAZAMENTO_COMUM} do enum {@link TipoAlertaEnum} é gravado como
 * {@code VAZAMENTO_CONTINUO}, que é o valor aceito pelo CHECK da coluna {@code ds_tipo_alerta}.</p>
 */
public class AlertaDAO {

    /**
     * Cadastra um novo alerta. O id gerado é preenchido no próprio objeto.
     *
     * @param alerta alerta a ser cadastrado
     */
    public void cadastrar(Alerta alerta) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "INSERT INTO Alerta (ds_tipo_alerta, ds_tipo_gravidade, ds_status_alerta, " +
                    "dt_identificacao, dt_reconhecimento, dt_resolucao, vl_volume_desperdicado, " +
                    "id_residencia, id_dispositivo, id_morador) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            ps = conexao.prepareStatement(sql, new String[]{"ID_ALERTA"});
            preencher(ps, alerta);
            ps.executeUpdate();

            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    alerta.setIdAlerta(rsGerado.getInt(1));
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
     * Atualiza todos os dados de um alerta já cadastrado.
     *
     * @param alerta alerta com os dados atualizados (o id é usado como referência)
     */
    public void alterar(Alerta alerta) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "UPDATE Alerta SET ds_tipo_alerta = ?, ds_tipo_gravidade = ?, ds_status_alerta = ?, " +
                    "dt_identificacao = ?, dt_reconhecimento = ?, dt_resolucao = ?, vl_volume_desperdicado = ?, " +
                    "id_residencia = ?, id_dispositivo = ?, id_morador = ? WHERE id_alerta = ?";

            ps = conexao.prepareStatement(sql);
            preencher(ps, alerta);
            ps.setInt(11, alerta.getIdAlerta());

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
     * Remove um alerta que ainda não foi resolvido. Alertas resolvidos fazem parte do histórico e
     * nunca são removidos: nesse caso nada é apagado.
     *
     * @param idAlerta identificador do alerta a ser removido
     */
    public void excluir(int idAlerta) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(
                    "DELETE FROM Alerta WHERE id_alerta = ? AND ds_status_alerta <> 'RESOLVIDO'");
            ps.setInt(1, idAlerta);
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
     * Busca um alerta pelo seu identificador.
     *
     * @param idAlerta identificador do alerta
     * @return o alerta encontrado, ou {@code null} caso não exista
     */
    public Alerta buscarPorId(int idAlerta) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        Alerta alerta = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Alerta WHERE id_alerta = ?");
            ps.setInt(1, idAlerta);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                alerta = montar(rs);
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
        return alerta;
    }

    /**
     * Lista os alertas de uma residência, do mais recente para o mais antigo (data de identificação
     * e, no empate, id). O período filtra pela data de identificação e é inclusivo nos dois
     * extremos; cada extremo é opcional ({@code null} não filtra).
     *
     * @param idResidencia identificador da residência
     * @param de           início do período
     * @param ate          fim do período
     * @return alertas da residência que atendem aos filtros
     */
    public List<Alerta> listarPorResidencia(int idResidencia, LocalDateTime de, LocalDateTime ate) {
        List<Alerta> alertas = new ArrayList<>();
        String sql = "SELECT * FROM Alerta WHERE id_residencia = ? " +
                (de != null ? "AND dt_identificacao >= ? " : "") +
                (ate != null ? "AND dt_identificacao <= ? " : "") +
                "ORDER BY dt_identificacao DESC, id_alerta DESC";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            int indice = 1;
            ps.setInt(indice++, idResidencia);
            if (de != null) {
                ps.setTimestamp(indice++, Timestamp.valueOf(de));
            }
            if (ate != null) {
                ps.setTimestamp(indice, Timestamp.valueOf(ate));
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                alertas.add(montar(rs));
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
        return alertas;
    }

    /**
     * Lista alertas do mais recente para o mais antigo (data de identificação e, no empate, id),
     * combinando os filtros informados. Todo filtro é opcional ({@code null} não filtra); o período
     * é inclusivo nos dois extremos e olha a data de identificação.
     *
     * @param idResidencia só alertas desta residência
     * @param idMorador    só alertas de residências às quais este morador tem vínculo
     * @param tipo         só alertas deste tipo
     * @param gravidade    só alertas desta gravidade
     * @param status       só alertas neste status
     * @param de           início do período
     * @param ate          fim do período
     * @return alertas que atendem aos filtros
     */
    public List<Alerta> listarFiltrado(Integer idResidencia, Integer idMorador, TipoAlertaEnum tipo,
                                       TipoGravidadeEnum gravidade, StatusAlertaEnum status,
                                       LocalDateTime de, LocalDateTime ate) {
        List<Alerta> alertas = new ArrayList<>();
        String sql = "SELECT a.* FROM Alerta a WHERE 1 = 1 " +
                (idResidencia != null ? "AND a.id_residencia = ? " : "") +
                (idMorador != null ? "AND EXISTS (SELECT 1 FROM Morador_Residencia mr " +
                        "WHERE mr.id_residencia = a.id_residencia AND mr.id_morador = ?) " : "") +
                (tipo != null ? "AND a.ds_tipo_alerta = ? " : "") +
                (gravidade != null ? "AND a.ds_tipo_gravidade = ? " : "") +
                (status != null ? "AND a.ds_status_alerta = ? " : "") +
                (de != null ? "AND a.dt_identificacao >= ? " : "") +
                (ate != null ? "AND a.dt_identificacao <= ? " : "") +
                "ORDER BY a.dt_identificacao DESC, a.id_alerta DESC";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            int indice = 1;
            if (idResidencia != null) {
                ps.setInt(indice++, idResidencia);
            }
            if (idMorador != null) {
                ps.setInt(indice++, idMorador);
            }
            if (tipo != null) {
                ps.setString(indice++, tipoParaBanco(tipo));
            }
            if (gravidade != null) {
                ps.setString(indice++, gravidade.name());
            }
            if (status != null) {
                ps.setString(indice++, status.name());
            }
            if (de != null) {
                ps.setTimestamp(indice++, Timestamp.valueOf(de));
            }
            if (ate != null) {
                ps.setTimestamp(indice, Timestamp.valueOf(ate));
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                alertas.add(montar(rs));
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
        return alertas;
    }

    /**
     * Lista todos os alertas cadastrados, em uma única consulta.
     *
     * @return lista de alertas cadastrados
     */
    public List<Alerta> listar() {
        List<Alerta> alertas = new ArrayList<>();
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Alerta ORDER BY dt_identificacao DESC, id_alerta DESC");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                alertas.add(montar(rs));
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
        return alertas;
    }

    /**
     * Atualiza somente o andamento do alerta: status, datas de reconhecimento e de resolução e
     * morador associado. Tipo, gravidade, data de identificação, volume desperdiçado, residência e
     * dispositivo não são modificados.
     *
     * @param alerta alerta com o andamento já atualizado (o id é usado como referência)
     */
    public void atualizarStatus(Alerta alerta) {
        String sql = "UPDATE Alerta SET " +
                "ds_status_alerta = ?, " +
                "dt_reconhecimento = ?, " +
                "dt_resolucao = ?, " +
                "id_morador = ? " +
                "WHERE id_alerta = ?";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setString(1, alerta.getStatusAlerta().name());

            setDataHora(ps, 2, alerta.getDataReconhecimento());
            setDataHora(ps, 3, alerta.getDataResolucao());

            if (alerta.getMorador() != null) {
                ps.setInt(4, alerta.getMorador().getIdMorador());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            ps.setInt(5, alerta.getIdAlerta());

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
     * Grava os campos do alerta nos parâmetros 1 a 10 do statement (mesma ordem no INSERT e no
     * UPDATE).
     *
     * @param ps     statement a ser preenchido
     * @param alerta alerta com os dados a gravar
     * @throws SQLException se algum parâmetro não puder ser gravado
     */
    private void preencher(PreparedStatement ps, Alerta alerta) throws SQLException {
        ps.setString(1, tipoParaBanco(alerta.getTipoAlerta()));
        ps.setString(2, alerta.getTipoGravidade().name());
        ps.setString(3, alerta.getStatusAlerta().name());
        setDataHora(ps, 4, alerta.getDataIdentificacao());
        setDataHora(ps, 5, alerta.getDataReconhecimento());
        setDataHora(ps, 6, alerta.getDataResolucao());
        if (alerta.getVolumeDesperdicado() != null) {
            ps.setDouble(7, alerta.getVolumeDesperdicado());
        } else {
            ps.setNull(7, Types.NUMERIC);
        }
        ps.setInt(8, alerta.getResidencia().getIdResidencia());
        ps.setInt(9, alerta.getDispositivo().getIdDispositivo());
        if (alerta.getMorador() != null) {
            ps.setInt(10, alerta.getMorador().getIdMorador());
        } else {
            ps.setNull(10, Types.INTEGER);
        }
    }

    /**
     * Monta o alerta a partir da linha atual do resultado, lendo as colunas pelo nome.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return o alerta montado
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Alerta montar(ResultSet rs) throws SQLException {
        Alerta alerta = new Alerta();
        alerta.setIdAlerta(rs.getInt("id_alerta"));
        alerta.setTipoAlerta(tipoDoBanco(rs.getString("ds_tipo_alerta")));
        alerta.setTipoGravidade(TipoGravidadeEnum.valueOf(rs.getString("ds_tipo_gravidade")));
        alerta.setStatusAlerta(StatusAlertaEnum.valueOf(rs.getString("ds_status_alerta")));
        alerta.setDataIdentificacao(lerDataHora(rs.getTimestamp("dt_identificacao")));
        alerta.setDataReconhecimento(lerDataHora(rs.getTimestamp("dt_reconhecimento")));
        alerta.setDataResolucao(lerDataHora(rs.getTimestamp("dt_resolucao")));

        double volume = rs.getDouble("vl_volume_desperdicado");
        alerta.setVolumeDesperdicado(rs.wasNull() ? null : volume);

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(rs.getInt("id_residencia"));
        alerta.setResidencia(residencia);

        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setIdDispositivo(rs.getInt("id_dispositivo"));
        alerta.setDispositivo(dispositivo);

        int idMorador = rs.getInt("id_morador");
        if (!rs.wasNull()) {
            Morador morador = new Morador();
            morador.setIdMorador(idMorador);
            alerta.setMorador(morador);
        }
        return alerta;
    }

    private static final String VAZAMENTO_NO_BANCO = "VAZAMENTO_CONTINUO";

    /**
     * Converte o tipo do alerta para o nome que o banco espera.
     */
    private String tipoParaBanco(TipoAlertaEnum tipo) {
        return tipo == TipoAlertaEnum.VAZAMENTO_COMUM ? VAZAMENTO_NO_BANCO : tipo.name();
    }

    /**
     * Converte o nome do tipo gravado no banco para o enum.
     */
    private TipoAlertaEnum tipoDoBanco(String valor) {
        return VAZAMENTO_NO_BANCO.equals(valor) ? TipoAlertaEnum.VAZAMENTO_COMUM : TipoAlertaEnum.valueOf(valor);
    }

    /**
     * Grava uma {@link LocalDateTime} (ou NULL) no parâmetro indicado.
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
     */
    private LocalDateTime lerDataHora(Timestamp dataHora) {
        return dataHora != null ? dataHora.toLocalDateTime() : null;
    }
}