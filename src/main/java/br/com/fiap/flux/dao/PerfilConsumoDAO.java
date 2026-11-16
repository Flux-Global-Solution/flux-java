package br.com.fiap.flux.dao;

import br.com.fiap.flux.models.PerfilConsumo;
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
 * {@link PerfilConsumo} na tabela {@code Perfil_Consumo}.
 *
 * <p>O id é gerado pelo banco (IDENTITY). O relacionamento com {@link Residencia} é gravado apenas
 * pela chave estrangeira ({@code id_residencia}); ao ler, a residência vem só com o id. Cada
 * residência deve ter um único perfil: o serviço usa {@link #buscarPorResidencia(int)} para
 * atualizar o existente em vez de criar outro.</p>
 */
public class PerfilConsumoDAO {

    /**
     * Cadastra um novo perfil de consumo. O id gerado é preenchido no próprio objeto.
     *
     * @param perfil perfil de consumo a ser cadastrado
     * @throws IllegalStateException se o banco falhar
     */
    public void cadastrar(PerfilConsumo perfil) {
        String sql = "INSERT INTO Perfil_Consumo (vl_consumo_medio, vl_vazao_media, vl_desvio_padrao, " +
                "dt_ultima_atualizacao, ds_padrao_consumo, nr_historico_suficiente, id_residencia) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_PERFIL_CONSUMO"})) {
            ps.setDouble(1, perfil.getConsumoMedio());
            ps.setDouble(2, perfil.getVazaoMedia());
            ps.setDouble(3, perfil.getDesvioPadrao());
            setData(ps, 4, perfil.getUltimaAtualizacao());
            ps.setString(5, perfil.getPadraoConsumo());
            ps.setInt(6, perfil.getHistoricoSuficiente());
            ps.setInt(7, perfil.getResidencia().getIdResidencia());
            ps.executeUpdate();

            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    perfil.setIdPerfilConsumo(rsGerado.getInt(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível cadastrar o perfil de consumo.", e);
        }
    }

    /**
     * Atualiza os dados calculados de um perfil já cadastrado. A residência do perfil não muda.
     *
     * @param perfil perfil de consumo com os dados atualizados (o id é usado como referência)
     * @throws IllegalStateException se o banco falhar
     */
    public void alterar(PerfilConsumo perfil) {
        String sql = "UPDATE Perfil_Consumo SET vl_consumo_medio = ?, vl_vazao_media = ?, " +
                "vl_desvio_padrao = ?, dt_ultima_atualizacao = ?, ds_padrao_consumo = ?, " +
                "nr_historico_suficiente = ? WHERE id_perfil_consumo = ?";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, perfil.getConsumoMedio());
            ps.setDouble(2, perfil.getVazaoMedia());
            ps.setDouble(3, perfil.getDesvioPadrao());
            setData(ps, 4, perfil.getUltimaAtualizacao());
            ps.setString(5, perfil.getPadraoConsumo());
            ps.setInt(6, perfil.getHistoricoSuficiente());
            ps.setInt(7, perfil.getIdPerfilConsumo());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível atualizar o perfil de consumo.", e);
        }
    }

    /**
     * Remove um perfil de consumo do banco de dados.
     *
     * @param idPerfilConsumo identificador do perfil a ser removido
     * @throws IllegalStateException se o banco falhar
     */
    public void excluir(int idPerfilConsumo) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("DELETE FROM Perfil_Consumo WHERE id_perfil_consumo = ?")) {
            ps.setInt(1, idPerfilConsumo);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível excluir o perfil de consumo.", e);
        }
    }

    /**
     * Busca um perfil de consumo pelo seu identificador.
     *
     * @param idPerfilConsumo identificador do perfil
     * @return o perfil encontrado, ou {@code null} caso não exista
     * @throws IllegalStateException se o banco falhar
     */
    public PerfilConsumo buscarPorId(int idPerfilConsumo) {
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM Perfil_Consumo WHERE id_perfil_consumo = ?")) {
            ps.setInt(1, idPerfilConsumo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? montar(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível consultar o perfil de consumo.", e);
        }
    }

    /**
     * Busca o perfil de consumo de uma residência. Se houver mais de um, o que o serviço não cria,
     * devolve o atualizado mais recentemente.
     *
     * @param idResidencia identificador da residência
     * @return o perfil encontrado, ou {@code null} se a residência ainda não tem perfil
     * @throws IllegalStateException se o banco falhar
     */
    public PerfilConsumo buscarPorResidencia(int idResidencia) {
        String sql = "SELECT * FROM Perfil_Consumo WHERE id_residencia = ? " +
                "ORDER BY dt_ultima_atualizacao DESC, id_perfil_consumo DESC FETCH FIRST 1 ROWS ONLY";
        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idResidencia);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? montar(rs) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível consultar o perfil de consumo da residência.", e);
        }
    }

    /**
     * Lista todos os perfis de consumo cadastrados, em uma única consulta.
     *
     * @return lista de perfis de consumo cadastrados
     * @throws IllegalStateException se o banco falhar
     */
    public List<PerfilConsumo> listar() {
        List<PerfilConsumo> perfis = new ArrayList<>();

        try (Connection con = ConnectionFactory.obterConexao();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM Perfil_Consumo ORDER BY id_perfil_consumo");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                perfis.add(montar(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException("Não foi possível listar os perfis de consumo.", e);
        }
        return perfis;
    }

    /**
     * Monta o perfil a partir da linha atual do resultado, lendo as colunas pelo nome.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return o perfil montado
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private PerfilConsumo montar(ResultSet rs) throws SQLException {
        PerfilConsumo perfil = new PerfilConsumo();
        perfil.setIdPerfilConsumo(rs.getInt("id_perfil_consumo"));
        perfil.setConsumoMedio(rs.getDouble("vl_consumo_medio"));
        perfil.setVazaoMedia(rs.getDouble("vl_vazao_media"));
        perfil.setDesvioPadrao(rs.getDouble("vl_desvio_padrao"));
        perfil.setUltimaAtualizacao(lerData(rs.getDate("dt_ultima_atualizacao")));
        perfil.setPadraoConsumo(rs.getString("ds_padrao_consumo"));
        perfil.setHistoricoSuficiente(rs.getInt("nr_historico_suficiente"));

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(rs.getInt("id_residencia"));
        perfil.setResidencia(residencia);
        return perfil;
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