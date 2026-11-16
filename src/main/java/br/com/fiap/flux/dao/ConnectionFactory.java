package br.com.fiap.flux.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fábrica responsável por criar as conexões com o banco de dados Oracle utilizado pela plataforma
 * Flux.
 *
 * <p>Os dados de acesso vêm das variáveis de ambiente {@code SPRING_DATASOURCE_URL},
 * {@code SPRING_DATASOURCE_USERNAME} e {@code SPRING_DATASOURCE_PASSWORD}.</p>
 */
public class ConnectionFactory {

    /**
     * Abre uma nova conexão com o banco de dados. Quem chama é responsável por fechá-la.
     *
     * @return uma conexão aberta com o banco Oracle
     * @throws IllegalStateException se alguma variável de ambiente não estiver configurada
     * @throws RuntimeException      se a conexão com o banco falhar
     */
    public static Connection obterConexao() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        String usuario = System.getenv("SPRING_DATASOURCE_USERNAME");
        String senha = System.getenv("SPRING_DATASOURCE_PASSWORD");

        if (url == null || usuario == null || senha == null) {
            throw new IllegalStateException(
                    "Variáveis de ambiente do Oracle não configuradas."
            );
        }

        try {
            return DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao conectar ao banco Oracle.", e
            );
        }
    }

}