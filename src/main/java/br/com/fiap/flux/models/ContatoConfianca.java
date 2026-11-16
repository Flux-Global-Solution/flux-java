package br.com.fiap.flux.models;

import br.com.fiap.flux.enums.TipoContatoEnum;

/**
 * Representa uma pessoa ou associação de confiança que pode receber avisos de uma residência sem
 * ter conta nem acesso ao sistema.
 *
 * <p>O contato é classificado por {@link TipoContatoEnum} e só é notificado depois de ter uma
 * {@link AutorizacaoContato} ativa.</p>
 */
public class ContatoConfianca {
    private int idContato;
    private String nome;
    private String telefone;
    private String email;
    private TipoContatoEnum tipoContato;

    public int getIdContato() {
        return idContato;
    }

    public void setIdContato(int idContato) {
        this.idContato = idContato;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public TipoContatoEnum getTipoContato() {
        return tipoContato;
    }

    public void setTipoContato(TipoContatoEnum tipoContato) {
        this.tipoContato = tipoContato;
    }
}