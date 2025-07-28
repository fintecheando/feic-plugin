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
public class InformacionEconomica {
    private String fecha;
    private String establecimientoInformacion;
    private String actividadEconomicaRTU;
    private ArrayList<MontosIngresos> montosIngresos;
    private ArrayList<MontosEgresos> montosEgresos;
    private ArrayList<OtrosIngresos> otrosIngresos;
    private String propositoRC;
}
