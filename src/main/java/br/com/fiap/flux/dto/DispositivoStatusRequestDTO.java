package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.StatusDispositivoEnum;
import br.com.fiap.flux.enums.StatusEnergiaEnum;

/**
 * Pedido para alterar o status simulado de um dispositivo (demonstração; não vem de hardware).
 * Cada campo é opcional: só os informados são alterados, mas ao menos um precisa vir.
 * Valores: dispositivo (ATIVO, INATIVO, MANUTENCAO, DESCONECTADO), sensorOk (true = funcionando, false = possível falha)
 * e energia (NORMAL, BATERIA_BAIXA, SEM_ENERGIA).
 */
public record DispositivoStatusRequestDTO(
        StatusDispositivoEnum statusDispositivo,
        Boolean sensorOk,
        StatusEnergiaEnum statusEnergia
) {
}