package org.group10.event.dto;

/**
 * Orden de la búsqueda de eventos. En los tres, a igual valor va primero el evento más próximo.
 */
public enum EventSort {
    /** Por fecha, el más próximo primero. */
    DATE,
    /** Por precio "desde" (el sector más barato), de menor a mayor. */
    PRICE,
    /** Con {@code q}: primero si coincide en el nombre, después en un participante y después en la descripción. Sin {@code q}, igual que {@code DATE}. */
    RELEVANCE
}
