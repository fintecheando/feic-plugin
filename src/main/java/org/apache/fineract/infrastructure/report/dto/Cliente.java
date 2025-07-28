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
public class Cliente {
    private String primerApellido;
    private String segundoApellido;
    private String apellidoCasada;
    private String primerNombre;
    private String segundoNombre;
    private String otrosNombres;
    private String fechaNacimiento;
    private ArrayList<String> nacionalidades;
    private Nacimiento nacimiento;
    private Integer condicionMigratoria;
    private String otraCondicionMigratoria;
    private String sexo;
    private String estadoCivil;
    private String profesionOficio;
    private String tipoDocumentoIdentificacion;
    private String numeroDocumentoIdentificacion;
    private String emisionPasaporte;
    private String nit;
    private ArrayList telefonos;
    private String email;
    private String direccionResidencia;
    private String zona;
    private Residencia residencia;
    private String pep;
    private String datosPep;
    private String parientePep;
    private String asociadoPep;
    private String cpe;
    
}
