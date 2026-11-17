package br.com.fiap.flux.dao;

import br.com.fiap.flux.enums.TipoVinculoEnum;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe de acesso a dados (DAO) responsável pelas operações de persistência de {@link Residencia}
 * na tabela {@code Residencia}.
 *
 * <p>O id é gerado pelo banco (IDENTITY) e a data de cadastro usa o padrão da tabela (SYSDATE). Os
 * horários de menor consumo são colunas {@code DATE}; como só interessa a hora do dia, são gravados
 * com uma data fixa e lidos de volta como {@link LocalTime}.</p>
 */
public class ResidenciaDAO {

    private static final LocalDate DATA_BASE_HORARIO = LocalDate.of(2000, 1, 1);

    /**
     * Residência junto do vínculo que o morador consultado tem com ela.
     *
     * @param residencia  residência encontrada
     * @param tipoVinculo tipo de vínculo do morador com a residência
     */
    public record ResidenciaComVinculo(Residencia residencia, TipoVinculoEnum tipoVinculo) {
    }

    /**
     * Cadastra a residência e o vínculo do titular em uma única transação: ou grava os dois, ou não
     * grava nenhum. O id gerado é preenchido em {@code residencia}.
     *
     * @param residencia residência a ser cadastrada
     * @param idMorador  identificador do morador que será o titular
     */
    public void cadastrarComTitular(Residencia residencia, int idMorador) {
        Connection conexao = ConnectionFactory.obterConexao();

        try {
            boolean confirmado = false;
            try {
                conexao.setAutoCommit(false);

                cadastrar(conexao, residencia);

                Morador morador = new Morador();
                morador.setIdMorador(idMorador);
                MoradorResidencia titular = new MoradorResidencia();
                titular.setTipoVinculo(TipoVinculoEnum.TITULAR);
                titular.setMorador(morador);
                titular.setResidencia(residencia);
                new MoradorResidenciaDAO().cadastrar(conexao, titular);

                conexao.commit();
                confirmado = true;
            } finally {
                // Desfaz antes de devolver a conexão: no Oracle, ligar o autocommit com a
                // transação aberta confirmaria tudo.
                if (!confirmado) {
                    conexao.rollback();
                }
                conexao.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            residencia.setIdResidencia(0);
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
     * Insere a residência na conexão (transação) recebida e preenche o id gerado.
     *
     * @param con        conexão (e transação) do chamador
     * @param residencia residência a ser cadastrada
     * @throws SQLException se o INSERT falhar
     */
    private void cadastrar(Connection con, Residencia residencia) throws SQLException {
        String sql = "INSERT INTO Residencia (ds_endereco, nr_moradores, ds_periodos_ausencia, " +
                "nr_limite_ponto_aberto_min, nr_limite_vazamento_min, " +
                "hr_inicio_menor_consumo, hr_fim_menor_consumo) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_RESIDENCIA"})) {
            ps.setString(1, residencia.getEndereco());
            ps.setInt(2, residencia.getQtdMoradores());
            ps.setString(3, residencia.getPeriodoAusencia());
            ps.setInt(4, residencia.getLimitePontoAberto());
            ps.setInt(5, residencia.getLimiteVazamentoMin());
            ps.setTimestamp(6, gravarHorario(residencia.getInicioMenorConsumo()));
            ps.setTimestamp(7, gravarHorario(residencia.getFimMenorConsumo()));
            ps.executeUpdate();

            try (ResultSet rsGerado = ps.getGeneratedKeys()) {
                if (rsGerado.next()) {
                    residencia.setIdResidencia(rsGerado.getInt(1));
                }
            }
        }
    }

    /**
     * Atualiza as configurações de uma residência. A data de cadastro não é alterada.
     *
     * @param residencia residência com os dados atualizados (o id é usado como referência)
     */
    public void alterar(Residencia residencia) {
        String sql = "UPDATE Residencia SET ds_endereco = ?, nr_moradores = ?, " +
                "ds_periodos_ausencia = ?, nr_limite_ponto_aberto_min = ?, " +
                "nr_limite_vazamento_min = ?, hr_inicio_menor_consumo = ?, " +
                "hr_fim_menor_consumo = ? " +
                "WHERE id_residencia = ?";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setString(1, residencia.getEndereco());
            ps.setInt(2, residencia.getQtdMoradores());
            ps.setString(3, residencia.getPeriodoAusencia());
            ps.setInt(4, residencia.getLimitePontoAberto());
            ps.setInt(5, residencia.getLimiteVazamentoMin());
            ps.setTimestamp(6, gravarHorario(residencia.getInicioMenorConsumo()));
            ps.setTimestamp(7, gravarHorario(residencia.getFimMenorConsumo()));
            ps.setInt(8, residencia.getIdResidencia());

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
     * Remove fisicamente uma residência e os vínculos dela, em uma única transação; as autorizações
     * de contato dos vínculos saem junto, pois dependem deles. Só deve ser chamado depois de
     * {@link #possuiHistorico(int)} confirmar que não há dispositivo, leitura, alerta, perfil nem
     * autorização. Se restar algum, as chaves estrangeiras barram a exclusão e nada é apagado.
     *
     * @param idResidencia identificador da residência a ser removida
     */
    public void excluir(int idResidencia) {
        String[] comandos = {
                "DELETE FROM Autorizacao_Contato WHERE id_morador_residencia IN " +
                        "(SELECT id_morador_residencia FROM Morador_Residencia WHERE id_residencia = ?)",
                "DELETE FROM Morador_Residencia WHERE id_residencia = ?",
                "DELETE FROM Residencia WHERE id_residencia = ?"
        };

        Connection conexao = ConnectionFactory.obterConexao();

        try {
            boolean confirmado = false;
            try {
                conexao.setAutoCommit(false);
                for (String sql : comandos) {
                    try (PreparedStatement ps = conexao.prepareStatement(sql)) {
                        ps.setInt(1, idResidencia);
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
     * Indica se a residência já tem dados que a política de exclusão precisa proteger: dispositivo
     * (e, portanto, leituras), alertas (e, portanto, notificações), perfil de consumo ou
     * autorizações de contatos de confiança (histórico de autorizações e revogações).
     *
     * @param idResidencia identificador da residência
     * @return {@code true} se existe algum desses registros
     */
    public boolean possuiHistorico(int idResidencia) {
        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM Dispositivo WHERE id_residencia = ?) + " +
                "(SELECT COUNT(*) FROM Alerta WHERE id_residencia = ?) + " +
                "(SELECT COUNT(*) FROM Perfil_Consumo WHERE id_residencia = ?) + " +
                "(SELECT COUNT(*) FROM Autorizacao_Contato ac " +
                "JOIN Morador_Residencia mr ON mr.id_morador_residencia = ac.id_morador_residencia " +
                "WHERE mr.id_residencia = ?) AS total FROM dual";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        boolean possui = false;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setInt(1, idResidencia);
            ps.setInt(2, idResidencia);
            ps.setInt(3, idResidencia);
            ps.setInt(4, idResidencia);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                possui = rs.getInt("total") > 0;
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
        return possui;
    }

    /**
     * Busca uma residência pelo seu identificador.
     *
     * @param idResidencia identificador da residência
     * @return a residência encontrada, ou {@code null} caso não exista
     */
    public Residencia buscarPorId(int idResidencia) {
        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;
        Residencia residencia = null;

        try {
            ps = conexao.prepareStatement("SELECT * FROM Residencia WHERE id_residencia = ?");
            ps.setInt(1, idResidencia);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                residencia = montar(rs);
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
        return residencia;
    }

    /**
     * Lista as residências às quais o morador tem acesso (qualquer vínculo), com o tipo de vínculo
     * dele em cada uma, em uma única consulta.
     *
     * @param idMorador identificador do morador
     * @return residências do morador, da mais antiga para a mais nova
     */
    public List<ResidenciaComVinculo> listarPorMorador(int idMorador) {
        List<ResidenciaComVinculo> residencias = new ArrayList<>();
        String sql = "SELECT r.*, mr.ds_tipo_vinculo FROM Residencia r " +
                "JOIN Morador_Residencia mr ON mr.id_residencia = r.id_residencia " +
                "WHERE mr.id_morador = ? ORDER BY r.id_residencia";

        Connection conexao = ConnectionFactory.obterConexao();
        PreparedStatement ps = null;

        try {
            ps = conexao.prepareStatement(sql);
            ps.setInt(1, idMorador);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                residencias.add(new ResidenciaComVinculo(
                        montar(rs), TipoVinculoEnum.valueOf(rs.getString("ds_tipo_vinculo"))));
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
        return residencias;
    }

    /**
     * Monta a residência a partir da linha atual do resultado, lendo as colunas pelo nome.
     *
     * @param rs resultado posicionado na linha a ser lida
     * @return a residência montada
     * @throws SQLException se alguma coluna não puder ser lida
     */
    private Residencia montar(ResultSet rs) throws SQLException {
        Residencia residencia = new Residencia();
        residencia.setIdResidencia(rs.getInt("id_residencia"));
        residencia.setEndereco(rs.getString("ds_endereco"));
        residencia.setQtdMoradores(rs.getInt("nr_moradores"));
        Date dtCadastro = rs.getDate("dt_cadastro");
        residencia.setDtCadastro(dtCadastro != null ? dtCadastro.toLocalDate() : null);
        residencia.setPeriodoAusencia(rs.getString("ds_periodos_ausencia"));
        residencia.setLimitePontoAberto(rs.getInt("nr_limite_ponto_aberto_min"));
        residencia.setLimiteVazamentoMin(rs.getInt("nr_limite_vazamento_min"));
        residencia.setInicioMenorConsumo(lerHorario(rs.getTimestamp("hr_inicio_menor_consumo")));
        residencia.setFimMenorConsumo(lerHorario(rs.getTimestamp("hr_fim_menor_consumo")));
        return residencia;
    }

    /**
     * Converte a hora do dia em {@link Timestamp}, usando uma data fixa, para gravar na coluna
     * {@code DATE}.
     *
     * @param horario hora do dia a gravar
     * @return o timestamp com a data fixa e o horário informado
     */
    private Timestamp gravarHorario(LocalTime horario) {
        return Timestamp.valueOf(LocalDateTime.of(DATA_BASE_HORARIO, horario));
    }

    /**
     * Converte o {@link Timestamp} da coluna {@code DATE} em hora do dia, tratando NULL.
     *
     * @param dataHora valor lido do banco, possivelmente {@code null}
     * @return a hora do dia, ou {@code null} se o valor for NULL
     */
    private LocalTime lerHorario(Timestamp dataHora) {
        return dataHora != null ? dataHora.toLocalDateTime().toLocalTime() : null;
    }
}