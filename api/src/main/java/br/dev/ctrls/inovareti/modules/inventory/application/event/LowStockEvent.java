package br.dev.ctrls.inovareti.modules.inventory.application.event;

import java.util.UUID;

/**
 * Evento emitido de forma assíncrona quando o estoque físico de um item
 * atinge ou fica abaixo do limiar estipulado em minStock.
 */
public record LowStockEvent(
    UUID itemId,
    String itemName,
    Integer currentStock,
    Integer minStock
) {}
