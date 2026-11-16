package br.com.fiap.flux.models;

import br.com.fiap.flux.enums.StatusContaEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

/**
 * Representa uma pessoa com conta no sistema FLUX, que pode se vincular a residências (ver
 * {@link MoradorResidencia}).
 *
 * <p>A senha nunca é guardada em texto puro: a classe mantém apenas o hash SHA-256, gerado por
 * {@link #definirSenha(String)} e conferido por {@link #senhaConfere(String)}. O status da conta
 * ({@link StatusContaEnum}) define se o morador pode entrar no sistema.</p>
 */
public class Morador {
    private int idMorador;
    private String nome;
    private String email;
    private String hashSenha;
    private String telefone;
    private StatusContaEnum statusConta;
    private LocalDate dataCadastro;

    public int getIdMorador() {
        return idMorador;
    }

    public void setIdMorador(int idMorador) {
        this.idMorador = idMorador;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHashSenha() {
        return hashSenha;
    }

    public void setHashSenha(String hashSenha) {
        this.hashSenha = hashSenha;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public StatusContaEnum getStatusConta() {
        return statusConta;
    }

    public void setStatusConta(StatusContaEnum statusConta) {
        this.statusConta = statusConta;
    }

    public LocalDate getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDate dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    /**
     * Define a senha do morador, guardando o hash SHA-256 dela (64 caracteres hexadecimais, o
     * tamanho da coluna {@code ds_senha_hash}).
     *
     * @param senha senha em texto puro
     */
    public void definirSenha(String senha) {
        this.hashSenha = gerarHash(senha);
    }

    /**
     * Confere se a senha informada corresponde ao hash guardado. A comparação é feita em tempo
     * constante.
     *
     * @param senha senha em texto puro a ser conferida
     * @return {@code true} se a senha confere; {@code false} se ela ou o hash guardado forem
     *     {@code null}, ou se não corresponderem
     */
    public boolean senhaConfere(String senha) {
        if (senha == null || hashSenha == null) {
            return false;
        }
        return MessageDigest.isEqual(
                gerarHash(senha).getBytes(StandardCharsets.UTF_8),
                hashSenha.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gera o hash SHA-256 de uma senha.
     *
     * @param senha senha em texto puro
     * @return o hash em 64 caracteres hexadecimais
     * @throws IllegalStateException se o algoritmo SHA-256 não estiver disponível
     */
    public static String gerarHash(String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(senha.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", e);
        }
    }

    /**
     * Indica se a conta do morador está ativa.
     *
     * @return {@code true} se o status da conta for {@code ATIVA}
     */
    public boolean estaAtivo() {
        return statusConta == StatusContaEnum.ATIVA;
    }
}