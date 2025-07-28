/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.fineract.infrastructure.report.dto;

import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Producto {
    private Lugar lugar;
    private String fecha;
    private String tipo;
    private String nombreDescripcion;
    private String identificador;
    private String nombreContrata;
    private String moneda;
    private Double valor;
    private String fondosPropios;
    private ArrayList otrosFirmantes;
    private ArrayList beneficiarios;
}
