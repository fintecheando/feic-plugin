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
public class Titular {
    private String tipoActuacion;
    private String calidadActua;
    private Lugar lugar;
    private String fecha;
    private Cliente cliente;
    private ArrayList<InformacionEconomica> infoEconomica;
}
