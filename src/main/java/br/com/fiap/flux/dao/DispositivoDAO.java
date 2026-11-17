package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.StatusDispositivoEnum;
import br.com.fiap.flux.enums.StatusEnergiaEnum;
import br.com.fiap.flux.models.Dispositivo;
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
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de {@link Dispositivo}
 * na tabela {@code Dispositivo}.
 *
 * <p>O id é gerado pelo banco (IDENTITY). A associação com {@link Residencia} é gravada pela chave
 * estrangeira {@code id_residencia}; ao ler, a residência vem só com o id. Série e MAC são únicos
 * no banco, o que garante que um dispositivo não esteja ligado a duas residências. A data da última
 * sincronização é opcional (NULL enquanto o dispositivo nunca tiver sincronizado).</p>
 *
 * <p>A coluna {@code ds_status_sensor} guarda {@code OK} ou {@code POSSIVEL_FALHA}; no modelo, o
 * sensor é um {@code boolean}, e a conversão é feita aqui.</p>
 */
public class DispositivoDAO {

    private static final String SENSOR_OK = "OK";
    private static final String SENSOR_POSSIVEL_FALHA = "POSSIVEL_FALHA";

    /**
     * Cadastra um novo dispositivo. O id gerado é preenchido no próprio objeto.
     *
     * @param dispositivo dispositivo a ser cadastrado
     */
    public void cadastrar(Dispositivo dispositivo) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "INSERT INTO Dispositivo (ds_serie, ds_mac_address, ds_modelo, dt_instalacao, " +
                    "ds_status_dispositivo, ds_status_sensor, ds_status_energia, " +
                    "dt_ultima_sincronizacao, id_residencia) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            ps = conexao.prepareStatement(sql, new String[]{"ID_DISPOSITIVO"});
            ps.setString(1, dispositivo.getSerie());
            ps.setString(2, dispositivo.getMacAddress());
            ps.setString(3, dispositivo.getModelo());
            setData(ps, 4, dispositivo.getDataInstalacao());
            ps.setString(5, dispositivo.getStatusDispositivo().toString());
            ps.setString(6, sensorParaBanco(dispositivo.isSensorOk()));
            ps.setString(7, dispositivo.getStatusEnergia().toString());
            setData(ps, 8, dispositivo.getUltimaSincronizacao());
            ps.setInt(9, dispositivo.getResidencia().getIdResidencia());

            ps.executeUpdate();

            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    dispositivo.setIdDispositivo(rsGerado.getInt(1));
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
     * Atualiza os dados de cadastro (série, MAC, modelo e data de instalação). Os status e a
     * residência não mudam por aqui: os status têm {@link #alterarStatus(Dispositivo)} e a
     * residência é fixa.
     *
     * @param dispositivo dispositivo com os dados atualizados (o id é usado como referência)
     */
    public void alterar(Dispositivo dispositivo) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "UPDATE Dispositivo SET ds_serie = ?, ds_mac_address = ?, ds_modelo = ?, " +
                    "dt_instalacao = ? WHERE id_dispositivo = ?";

            ps = conexao.prepareStatement(sql);
            ps.setString(1, dispositivo.getSerie());
            ps.setString(2, dispositivo.getMacAddress());
            ps.setString(3, dispositivo.getModelo());
            setData(ps, 4, dispositivo.getDataInstalacao());
            ps.setInt(5, dispositivo.getIdDispositivo());

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
     * Grava os três status simulados do dispositivo (dispositivo, sensor e energia). A data de
     * sincronização não muda.
     *
     * @param dispositivo dispositivo com os status já definidos (o id é usado como referência)
     */
    public void alterarStatus(Dispositivo dispositivo) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            String sql = "UPDATE Dispositivo SET ds_status_dispositivo = ?, ds_status_sensor = ?, " +
                    "ds_status_energia = ? WHERE id_dispositivo = ?";

            ps = conexao.prepareStatement(sql);
            ps.setString(1, dispositivo.getStatusDispositivo().toString());
            ps.setString(2, sensorParaBanco(dispositivo.isSensorOk()));
            ps.setString(3, dispositivo.getStatusEnergia().toString());
            ps.setInt(4, dispositivo.getIdDispositivo());

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
     * Remove um dispositivo do banco de dados.
     *
     * @param idDispositivo identificador do dispositivo a ser removido
     */
    public void excluir(int idDispositivo) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("DELETE FROM Dispositivo WHERE id_dispositivo = ?");
            ps.setInt(1, idDispositivo);
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
     * Busca um dispositivo pelo seu identificador.
     *
     * @param idDispositivo identificador do dispositivo
     * @return o dispositivo encontrado, ou {@code null} caso não exista
     */
    public Dispositivo buscarPorId(int idDispositivo) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        Dispositivo dispositivo = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Dispositivo WHERE id_dispositivo = ?");
            ps.setInt(1, idDispositivo);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                dispositivo = montar(rs);
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
        return dispositivo;
    }

    /**
     * Busca os dispositivos que já usam o número de série ou o MAC informados. Serve para impedir
     * que um dispositivo já vinculado seja associado a outra residência.
     *
     * @param serie      número de série
     * @param macAddress endereço MAC
     * @return dispositivos encontrados (no máximo um por série e um por MAC)
     */
    public List<Dispositivo> buscarPorSerieOuMac(String serie, String macAddress) {
        List<Dispositivo> dispositivos = new ArrayList<>();
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Dispositivo WHERE ds_serie = ? OR ds_mac_address = ?");
            ps.setString(1, serie);
            ps.setString(2, macAddress);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                dispositivos.add(montar(rs));
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
        return dispositivos;
    }

    /**
     * Busca o dispositivo instalado em uma residência. Se houver mais de um, devolve o instalado
     * mais recentemente.
     *
     * @param idResidencia identificador da residência
     * @return o dispositivo encontrado, ou {@code null} se a residência não tem dispositivo
     */
    public Dispositivo buscarPorResidencia(int idResidencia) {
        String sql = "SELECT * FROM Dispositivo WHERE id_residencia = ? " +
                "ORDER BY dt_instalacao DESC, id_dispositivo DESC FETCH FIRST 1 ROWS ONLY";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        Dispositivo dispositivo = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setInt(1, idResidencia);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                dispositivo = montar(rs);
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
        return dispositivo;
    }

    /**
     * Lista os dispositivos instalados em uma residência.
     *
     * @param idResidencia identificador da residência
     * @return dispositivos da residência (vazio se não houver)
     */
    public List<Dispositivo> listarPorResidencia(int idResidencia) {
        List<Dispositivo> dispositivos = new ArrayList<>();
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Dispositivo WHERE id_residencia = ? ORDER BY id_dispositivo");
            ps.setInt(1, idResidencia);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                dispositivos.add(montar(rs));
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
        return dispositivos;
    }

    /**
     * Lista os dispositivos das residências às quais o morador tem acesso (qualquer vínculo), em
     * uma única consulta.
     *
     * @param idMorador identificador do morador
     * @return dispositivos que o morador pode ver
     */
    public List<Dispositivo> listarPorMorador(int idMorador) {
        List<Dispositivo> dispositivos = new ArrayList<>();
        String sql = "SELECT d.* FROM Dispositivo d " +
                "JOIN Morador_Residencia mr ON mr.id_residencia = d.id_residencia " +
                "WHERE mr.id_morador = ? ORDER BY d.id_dispositivo";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setInt(1, idMorador);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                dispositivos.add(montar(rs));
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
        return dispositivos;
    }

    /**
     * Lista todos os dispositivos cadastrados, em uma única consulta.
     *
     * @return lista de dispositivos cadastrados
     */
    public List<Dispositivo> listar() {
        List<Dispositivo> dispositivos = new ArrayList<>();
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Dispositivo");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                dispositivos.add(montar(rs));
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
        return dispositivos;
    }

    /**
     * Monta o dispositivo a partir da linha atual do resultado, lendo as colunas pelo nome.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return o dispositivo montado
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Dispositivo montar(ResultSet rs) throws SQLException {
        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setIdDispositivo(rs.getInt("id_dispositivo"));
        dispositivo.setSerie(rs.getString("ds_serie"));
        dispositivo.setMacAddress(rs.getString("ds_mac_address"));
        dispositivo.setModelo(rs.getString("ds_modelo"));
        dispositivo.setDataInstalacao(lerData(rs.getDate("dt_instalacao")));
        dispositivo.setStatusDispositivo(StatusDispositivoEnum.valueOf(rs.getString("ds_status_dispositivo")));
        dispositivo.setSensorOk(SENSOR_OK.equals(rs.getString("ds_status_sensor")));
        dispositivo.setStatusEnergia(StatusEnergiaEnum.valueOf(rs.getString("ds_status_energia")));
        dispositivo.setUltimaSincronizacao(lerData(rs.getDate("dt_ultima_sincronizacao")));

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(rs.getInt("id_residencia"));
        dispositivo.setResidencia(residencia);
        return dispositivo;
    }

    /**
     * Converte o estado do sensor para o texto gravado na coluna {@code ds_status_sensor}.
     *
     * @param sensorOk {@code true} se o sensor está funcionando
     * @return {@code OK} ou {@code POSSIVEL_FALHA}
     */
    private String sensorParaBanco(boolean sensorOk) {
        return sensorOk ? SENSOR_OK : SENSOR_POSSIVEL_FALHA;
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