package br.com.fiap.flux.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Funções de apoio compartilhadas pelos services: arredondamento, leitura de períodos de consulta e
 * dos períodos de ausência da residência.
 */
public final class ConsumoUtil {

    private static final Pattern DATA = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})|(\\d{2}/\\d{2}/\\d{4})");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ConsumoUtil() {
    }

    /** Intervalo de datas (inclusivo) em que a residência estava ausente. */
    public record Periodo(LocalDate inicio, LocalDate fim) {
        public boolean contem(LocalDate dia) {
            return !dia.isBefore(inicio) && !dia.isAfter(fim);
        }
    }

    /** Arredonda para duas casas decimais. */
    public static double arredondar(double valor) {
        return Math.round(valor * 100) / 100.0;
    }

    /**
     * Lê "yyyy-MM-dd" (início ou fim do dia, conforme {@code fimDoDia}) ou "yyyy-MM-ddTHH:mm[:ss]";
     * vazio vira {@code null}.
     *
     * @throws IllegalArgumentException se o texto não está num desses formatos
     */
    public static LocalDateTime lerPeriodo(String texto, String campo, boolean fimDoDia) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            String valor = texto.trim();
            if (valor.length() == 10) {
                LocalDate dia = LocalDate.parse(valor);
                return fimDoDia ? dia.atTime(23, 59, 59) : dia.atStartOfDay();
            }
            return LocalDateTime.parse(valor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida em '" + campo
                    + "' (use 2026-11-14 ou 2026-11-14T08:00).");
        }
    }

    /**
     * Lê os períodos de ausência do texto da residência: datas em "2026-11-08" ou "08/11/2026", em
     * pares (início e fim); o que não for data é ignorado. Uma data sobrando no fim vira um período de
     * um dia só, e um par invertido é corrigido.
     */
    public static List<Periodo> lerAusencias(String texto) {
        List<Periodo> periodos = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return periodos;
        }
        List<LocalDate> datas = new ArrayList<>();
        Matcher encontrada = DATA.matcher(texto);
        while (encontrada.find()) {
            try {
                datas.add(encontrada.group(1) != null
                        ? LocalDate.parse(encontrada.group(1))
                        : LocalDate.parse(encontrada.group(2), DATA_BR));
            } catch (DateTimeParseException e) {
                // data impossível (ex.: 2026-13-45): ignorada
            }
        }
        for (int i = 0; i < datas.size(); i += 2) {
            LocalDate inicio = datas.get(i);
            LocalDate fim = i + 1 < datas.size() ? datas.get(i + 1) : inicio;
            periodos.add(fim.isBefore(inicio) ? new Periodo(fim, inicio) : new Periodo(inicio, fim));
        }
        return periodos;
    }

    /** Diz se o dia cai em algum dos períodos de ausência. */
    public static boolean emAusencia(List<Periodo> ausencias, LocalDate dia) {
        return ausencias.stream().anyMatch(periodo -> periodo.contem(dia));
    }
}