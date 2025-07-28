/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.fineract.infrastructure.report.dto.FeicReport;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.ApiParameterHelper;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import static org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection.toJdbcUrl;
import static org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection.toProtocol;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabasePasswordEncryptor;
import org.apache.fineract.infrastructure.dataqueries.data.ReportExportType;
import org.apache.fineract.infrastructure.report.annotation.ReportService;
import org.apache.fineract.infrastructure.security.constants.TenantConstants;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.pentaho.reporting.engine.classic.core.ClassicEngineBoot;
import org.pentaho.reporting.engine.classic.core.CompoundDataFactory;
import org.pentaho.reporting.engine.classic.core.DataFactory;
import org.pentaho.reporting.engine.classic.core.DefaultReportEnvironment;
import org.pentaho.reporting.engine.classic.core.Element;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.Section;
import org.pentaho.reporting.engine.classic.core.SubReport;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.DriverConnectionProvider;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.SQLReportDataFactory;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.csv.CSVReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.ExcelReportUtil;
import org.pentaho.reporting.engine.classic.core.parameters.ParameterDefinitionEntry;
import org.pentaho.reporting.libraries.resourceloader.Resource;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.sql.ResultSet;
import org.apache.fineract.infrastructure.report.dto.Cliente;
import org.apache.fineract.infrastructure.report.dto.InformacionEconomica;
import org.apache.fineract.infrastructure.report.dto.Lugar;
import org.apache.fineract.infrastructure.report.dto.MontosEgresos;
import org.apache.fineract.infrastructure.report.dto.MontosIngresos;
import org.apache.fineract.infrastructure.report.dto.Nacimiento;
import org.apache.fineract.infrastructure.report.dto.Nacionalidad;
import org.apache.fineract.infrastructure.report.dto.OtrosIngresos;
import org.apache.fineract.infrastructure.report.dto.Producto;
import org.apache.fineract.infrastructure.report.dto.Residencia;
import org.apache.fineract.infrastructure.report.dto.Titular;
import org.springframework.jdbc.core.RowMapper;

@Service
@ReportService(type = "Pentaho")
public class PentahoReportingProcessServiceImpl implements ReportingProcessService {

    private static final Logger logger = LoggerFactory.getLogger(PentahoReportingProcessServiceImpl.class);
    private final String mifosBaseDir = System.getProperty("user.home") + File.separator + ".mifosx";
    private final DatabasePasswordEncryptor databasePasswordEncryptor;
    
    private final JdbcTemplate jdbcTemplate;

    @Value("${FINERACT_PENTAHO_REPORTS_PATH}")
    private String fineractPentahoBaseDir;

    private final PlatformSecurityContext context;
    private final DataSource tenantDataSource;

    @Autowired
    FineractProperties fineractProperties;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ApplicationContext contextVar;

    @Autowired
    public PentahoReportingProcessServiceImpl(final PlatformSecurityContext context,
            final @Qualifier("hikariTenantDataSource") DataSource tenantDataSource, 
            DatabasePasswordEncryptor databasePasswordEncryptor, JdbcTemplate jdbcTemplate) {
        ClassicEngineBoot.getInstance().start();
        this.tenantDataSource = tenantDataSource;
        this.context = context;
        this.databasePasswordEncryptor = databasePasswordEncryptor ;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SubReport> getSubReports(MasterReport masterReport) {
        List<SubReport> subReports = new ArrayList<>();
        collectSubReports(masterReport.getReportHeader(), subReports);
        collectSubReports(masterReport.getReportFooter(), subReports);
        collectSubReports(masterReport.getPageHeader(), subReports);
        collectSubReports(masterReport.getPageFooter(), subReports);
        collectSubReports(masterReport.getItemBand(), subReports);
        return subReports;
        }

        private void collectSubReports(Section section, List<SubReport> subReports) {
        if (section == null) {
            return;
        }
        for (int i = 0; i < section.getElementCount(); i++) {
            Element element = section.getElement(i);
            if (element instanceof SubReport subReport) {
            subReports.add(subReport);
            // Recursively collect subreports within subreports
            for (int j = 0; j < subReport.getElementCount(); j++) {
                if (subReport.getElement(j) instanceof Section subSection) {
                    collectSubReports(subSection, subReports);
                }
            }
            }
        }
        }

        @Override
        public Response processRequest(final String reportName, final MultivaluedMap<String, String> queryParams) {
        final var outputTypeParam = queryParams.getFirst("output-type");
        final var reportParams = getReportParams(queryParams);
        final var locale = ApiParameterHelper.extractLocale(queryParams);
        final var language = "en";

        var outputType = "HTML";
        if (StringUtils.isNotBlank(outputTypeParam)) {
            outputType = outputTypeParam;
        }

        if ((!outputType.equalsIgnoreCase("HTML") && !outputType.equalsIgnoreCase("PDF") && !outputType.equalsIgnoreCase("XLS")
                && !outputType.equalsIgnoreCase("XLSX") && !outputType.equalsIgnoreCase("CSV") && !outputType.equalsIgnoreCase("JSON"))) {
            throw new PlatformDataIntegrityException("error.msg.invalid.outputType", "No matching Output Type: " + outputType);
        }

        // final var reportPath = mifosBaseDir + File.separator + "pentahoReports" + File.separator + reportName +
        // ".prpt";
        String reportPath="";
        logger.debug("locale " + locale);
        logger.debug("language " + language);
        if(reportName.equalsIgnoreCase("ReporteFeic")){
            logger.debug("REPORTE FEIC ");
        }
        else if (!"en".equals(locale.toString().toLowerCase()) && locale != null) {
            reportPath = getReportPath() + reportName + "_" + locale.toString().toLowerCase() + ".prpt";
        } else {
            reportPath = getReportPath() + reportName + ".prpt";
        }
        var outPutInfo = "Report path: " + reportPath;
        logger.debug("Report path: {}", outPutInfo);

        // load report definition
        final var manager = new ResourceManager();
        manager.registerDefaults();
        Resource res;

        try {
            
            if (reportName.equalsIgnoreCase("ReporteFeic")) {   
                
                final var clientId = queryParams.get("R_clientId").get(0);
                logger.debug("VALOR PARAMETRO "+clientId);
                
                FeicReport feicReport = new FeicReport();
                Cliente cliente = new Cliente();
                InformacionEconomica informacionEconomica = new InformacionEconomica();
                Lugar lugar = new Lugar();
                MontosEgresos montosEgresos = new MontosEgresos();
                MontosIngresos montosIngresos = new MontosIngresos();
                OtrosIngresos otrosIngresos = new OtrosIngresos();
                Nacimiento nacimiento = new Nacimiento();
                Nacionalidad nacionalidad = new Nacionalidad();
                ArrayList<String> nacionalidades = new ArrayList<String>();
                Producto producto = new Producto();
                Residencia residencia = new Residencia();
                Titular titular = new Titular();
                ArrayList<Titular> titulares = new ArrayList<Titular>();
                ArrayList<Producto> productos = new ArrayList<Producto>();
                
                String sqlLugar ="SELECT ma.*, mcov.code_value FROM m_address ma, "
                        + "m_client_address mca, m_client mc, m_code mco, m_code_value mcov "
                        + "WHERE mc.id=mca.client_id and ma.id=mca.address_id and mca.is_active=1 "
                        + "and ma.country_id=mcov.id and mcov.code_id=mco.id "
                        + "and mc.id = ? ";
                
                RowMapper<Lugar> mapperLugar = new RowMapper<Lugar>() {
                    public Lugar mapRow(ResultSet rs, int rowNum) throws SQLException {
                        Lugar lugar = new Lugar();                                                
                        lugar.setPais(rs.getString("code_value"));                        
                        lugar.setMunicipio(rs.getString("city"));                        
                        return lugar;
                    }
                };
                
                lugar = (Lugar) jdbcTemplate.queryForObject(sqlLugar, new Object[] {clientId}, mapperLugar);
                
                String sqlProducto = "SELECT sa.account_no, sa.account_balance_derived, sa.activatedon_date, "
                        + "sa.currency_code, sp.name, sp.description FROM m_savings_account "
                        + "sa,m_savings_product sp WHERE sp.id=sa.product_id "  
                        + "and client_id = ?";
                
                RowMapper<Producto> mapperProducto = new RowMapper<Producto>() {
                    public Producto mapRow(ResultSet rs, int rowNum) throws SQLException {
                        Producto producto = new Producto();                        
                        producto.setValor(rs.getDouble("account_balance_derived"));
                        producto.setIdentificador(rs.getString("account_no"));
                        producto.setMoneda(rs.getString("currency_code"));
                        producto.setNombreDescripcion(rs.getString("description"));
                        producto.setTipo(rs.getString("name"));
                        producto.setFecha(rs.getString("activatedon_date").replace("-", ""));
                        return producto;
                    }
                };
                
                producto = (Producto) jdbcTemplate.queryForObject(sqlProducto, new Object[] {clientId}, mapperProducto);
                
                String sqlCliente = "SELECT firstname,lastname,date_of_birth,email_address FROM m_client " + 
                        " WHERE id = ?";
                
                RowMapper<Cliente> mapper = new RowMapper<Cliente>() {
                    public Cliente mapRow(ResultSet rs, int rowNum) throws SQLException {
                        Cliente cliente = new Cliente();   
                        cliente.setFechaNacimiento(rs.getString("date_of_birth").replace("-", ""));
                        cliente.setEmail(rs.getString("email_address"));
                        cliente.setPrimerNombre(rs.getString("firstname"));
                        cliente.setPrimerApellido(rs.getString("lastname"));
                        return cliente;
                    }
                };
                
                cliente = (Cliente) jdbcTemplate.queryForObject(sqlCliente, new Object[] {clientId}, mapper);
                
                producto.setFondosPropios("1");
                producto.setLugar(lugar);
                producto.setOtrosFirmantes(new ArrayList());
                producto.setBeneficiarios(new ArrayList());
                producto.setNombreContrata(cliente.getPrimerNombre() + " "+ cliente.getPrimerApellido());
                productos.add(producto);
                
                String sqlNacionalidad = "SELECT Nacionalidad_1,Nacionalidad_2 FROM DATOS_PERSONALES_DEL_CLIENTE " + 
                        " WHERE client_id = ?";
                
                RowMapper<Nacionalidad> mapperNacionalidad = new RowMapper<Nacionalidad>() {
                    public Nacionalidad mapRow(ResultSet rs, int rowNum) throws SQLException {
                        Nacionalidad nacionalidad = new Nacionalidad();                        
                        nacionalidad.setNacionalidad1(rs.getString("Nacionalidad_1"));
                        nacionalidad.setNacionalidad2(rs.getString("Nacionalidad_2"));
                        return nacionalidad;
                    }
                };
                
                nacionalidad = (Nacionalidad) jdbcTemplate.queryForObject(sqlNacionalidad, new Object[] {clientId}, mapperNacionalidad);
                
                if(nacionalidad.getNacionalidad1()!=null)
                    nacionalidades.add(nacionalidad.getNacionalidad1());
                if(nacionalidad.getNacionalidad2()!=null)
                    nacionalidades.add(nacionalidad.getNacionalidad2());
                cliente.setNacionalidades(nacionalidades);
                
                //residencia.setDepartamento(lugar.getDepartamento());
                residencia.setMunicipio(lugar.getMunicipio());
                residencia.setPais(lugar.getPais());
                cliente.setResidencia(residencia);
                        
                titular.setCliente(cliente);
                titulares.add(titular);
                feicReport.setTitulares(titulares);
                feicReport.setProductos(productos);
                //titular.setLugar(lugar);
                
                return Response.ok().entity(feicReport).type("application/json")
                        .header("Content-Disposition", "attachment;filename=" + reportName.replaceAll(" ", "") + ".json").build();

            }
            else {
                res = manager.createDirectly(reportPath, MasterReport.class);
                final var masterReport = (MasterReport) res.getResource();

                // Override Data Connection Factory with the driver and url
                CompoundDataFactory compoundDataFactory = (CompoundDataFactory) masterReport.getDataFactory();
                setConnectionDetail(compoundDataFactory.get(0));

                final var reportEnvironment = (DefaultReportEnvironment) masterReport.getReportEnvironment();
                if (locale != null) {
                    reportEnvironment.setLocale(locale);
                }

                addParametersToReport(masterReport, reportParams);

                List<SubReport> subReports = getSubReports(masterReport);
                for (SubReport subReport : subReports) {
                    CompoundDataFactory subReportCompoundDataFactory = (CompoundDataFactory) subReport.getDataFactory();
                    setConnectionDetail(subReportCompoundDataFactory.get(0));
                }

                final var baos = new ByteArrayOutputStream();

                if ("PDF".equalsIgnoreCase(outputType)) {
                    PdfReportUtil.createPDF(masterReport, baos);
                    return Response.ok().entity(baos.toByteArray()).type("application/pdf").build();

                } else if ("XLS".equalsIgnoreCase(outputType)) {
                    ExcelReportUtil.createXLS(masterReport, baos);
                    return Response.ok().entity(baos.toByteArray()).type("application/vnd.ms-excel")
                            .header("Content-Disposition", "attachment;filename=" + reportName.replaceAll(" ", "") + ".xls").build();

                } else if ("XLSX".equalsIgnoreCase(outputType)) {
                    ExcelReportUtil.createXLSX(masterReport, baos);
                    return Response.ok().entity(baos.toByteArray()).type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .header("Content-Disposition", "attachment;filename=" + reportName.replaceAll(" ", "") + ".xlsx").build();

                } else if ("CSV".equalsIgnoreCase(outputType)) {
                    CSVReportUtil.createCSV(masterReport, baos, "UTF-8");
                    return Response.ok().entity(baos.toByteArray()).type("text/csv")
                            .header("Content-Disposition", "attachment;filename=" + reportName.replaceAll(" ", "") + ".csv").build();

                } else if ("HTML".equalsIgnoreCase(outputType)) {
                    HtmlReportUtil.createStreamHTML(masterReport, baos);
                    return Response.ok().entity(baos.toByteArray()).type("text/html").build();

                }             
                else {
                    throw new PlatformDataIntegrityException("error.msg.invalid.outputType", "No matching Output Type: " + outputType);

                }
            }
        }
        catch (Throwable t) {
            logger.error("Pentaho failed", t);
            throw new PlatformDataIntegrityException("error.msg.reporting.error", "Pentaho failed: " + t.getMessage());
        }
    }

    private void addParametersToReport(final MasterReport report, final Map<String, String> queryParams) {
        final var currentUser = this.context.authenticatedUser();
        try {
            final var rptParamValues = report.getParameterValues();
            final var paramsDefinition = report.getParameterDefinition();

            //only allow integer, long, date and string parameter types and assume all mandatory - could go more
            // detailed like Pawel did in Mifos later and could match incoming and Pentaho parameters better...
            // currently assuming they come in ok... and if not an error
            for (final ParameterDefinitionEntry paramDefEntry : paramsDefinition.getParameterDefinitions()) {
                final var paramName = paramDefEntry.getName();
                if ((!paramName.equals("tenantUrl") && (!paramName.equals("userhierarchy") && !paramName.equals("username")
                        && (!paramName.equals("password") && !paramName.equals("userid"))))) {

                    var outPutInfo2 = "paramName:" + paramName;
                    logger.debug("paramName: {}", outPutInfo2);

                    final var pValue = queryParams.get(paramName);
                    if (StringUtils.isBlank(pValue)) {
                        throw new PlatformDataIntegrityException("error.msg.reporting.error",
                                "Pentaho Parameter: " + paramName + " - not Provided");
                    }

                    final Class<?> clazz = paramDefEntry.getValueType();
                    var outPutInfo3 = "addParametersToReport(" + paramName + " : " + pValue + " : " + clazz.getCanonicalName() + ")";
                    logger.debug("outputInfo: {}", outPutInfo3);

                    if (clazz.getCanonicalName().equalsIgnoreCase("java.lang.Integer")) {
                        rptParamValues.put(paramName, Integer.parseInt(pValue));
                    } else if (clazz.getCanonicalName().equalsIgnoreCase("java.lang.Long")) {
                        rptParamValues.put(paramName, Long.parseLong(pValue));
                    } else if (clazz.getCanonicalName().equalsIgnoreCase("java.sql.Date")) {
                        logger.debug("ParamName: {}", paramName);
                        logger.debug("ParamValue: {}", pValue.toString());
                        String myDate = pValue.toString();
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH);
                        Date date = sdf.parse(myDate);
                        long millis = date.getTime();
                        java.sql.Date mySQLDate = new java.sql.Date(millis);                        
                        rptParamValues.put(paramName, mySQLDate);                        
                    } else {
                        logger.debug("ParamName Unknown: {}", paramName);
                        logger.debug("ParamValue Unknown: {}", pValue.toString());
                        rptParamValues.put(paramName, pValue);
                    }
                }
            }

            //Tenant database name and current user's office hierarchy
            // passed as parameters to allow multitenant Pentaho reporting
            // and data scoping
            final var tenant = ThreadLocalContextUtil.getTenant();
            final var tenantConnection = tenant.getConnection();            
            String protocol = toProtocol(this.tenantDataSource);            
            Environment environment = contextVar.getEnvironment();
            String tenantUrl = toJdbcUrl(protocol, tenantConnection.getSchemaServer(), tenantConnection.getSchemaServerPort(),
                    tenantConnection.getSchemaName(), tenantConnection.getSchemaConnectionParameters());

            final var userhierarchy = currentUser.getOffice().getHierarchy();
            logger.debug("userhierarchy "+userhierarchy);
            var outPutInfo4 = "db URL:" + tenantUrl + "      userhierarchy:" + userhierarchy;
            logger.debug(outPutInfo4);

            rptParamValues.put("userhierarchy", userhierarchy);

            final var userid = currentUser.getId();
            var outPutInfo5 = "db URL:" + tenantUrl + "      userid:" + userid;
            logger.debug(outPutInfo5);

            rptParamValues.put("userid", userid);

            rptParamValues.put("tenantUrl", tenantUrl.trim());
            
            if (tenantConnection.getSchemaUsername().equalsIgnoreCase("") || tenantConnection.getSchemaUsername() == null) {
                rptParamValues.put("username", environment.getProperty("FINERACT_DEFAULT_TENANTDB_UID"));
            } else {
                rptParamValues.put("username", tenantConnection.getSchemaUsername().trim());
            }

            if (tenantConnection.getSchemaPassword().equalsIgnoreCase("") || tenantConnection.getSchemaPassword() == null) {                
                rptParamValues.put("password", environment.getProperty("FINERACT_DEFAULT_TENANTDB_PWD"));
            } else {
                rptParamValues.put("password", databasePasswordEncryptor.decrypt(tenantConnection.getSchemaPassword()).trim()); 
            }


        } catch (Throwable t) {
            logger.error("error.msg.reporting.error:", t);
            throw new PlatformDataIntegrityException("error.msg.reporting.error", t.getMessage());
        }
    }

    @Override
    public Map<String, String> getReportParams(final MultivaluedMap<String, String> queryParams) {
        final Map<String, String> reportParams = new HashMap<>();
        final var keys = queryParams.keySet();
        String pKey;
        String pValue;
        for (final String k : keys) {
            if (k.startsWith("R_")) {
                pKey = k.substring(2);
                pValue = queryParams.get(k).get(0);
                reportParams.put(pKey, pValue);
            }
        }
        return reportParams;
    }

    private String getReportPath() {
        if (fineractPentahoBaseDir != null) {
            return this.fineractPentahoBaseDir + File.separator;
        }
        return this.mifosBaseDir + File.separator + "pentahoReports" + File.separator;
    }

    private void setConnectionDetail(DataFactory dataFactory) throws SQLException {
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        final FineractPlatformTenantConnection tenantConnection = tenant.getConnection();

        if (dataFactory instanceof SQLReportDataFactory) {
            SQLReportDataFactory sqlReportDataFactory = (SQLReportDataFactory) dataFactory;
            final DriverConnectionProvider connectionProvider = (DriverConnectionProvider) sqlReportDataFactory.getConnectionProvider();

            Driver e = DriverManager.getDriver(getTenantUrl());
            // Printing the driver
            logger.debug("Driver: " + e.getClass().getName().toString());
            connectionProvider.setDriver(e.getClass().getName().toString());
            connectionProvider.setUrl(getTenantUrl());
            connectionProvider.setProperty("user", tenantConnection.getSchemaUsername());
            logger.debug("{}", tenantConnection.getSchemaUsername());
            connectionProvider.setProperty("password", databasePasswordEncryptor.decrypt(tenantConnection.getSchemaPassword()).trim());
            sqlReportDataFactory.setConnectionProvider(connectionProvider);
        }
    }

    private String getTenantUrl() {
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        final FineractPlatformTenantConnection tenantConnection = tenant.getConnection();
        String protocol = toProtocol(tenantDataSource);
        // Default properties for Writing
        String schemaServer = tenantConnection.getSchemaServer();
        String schemaPort = tenantConnection.getSchemaServerPort();
        String schemaName = tenantConnection.getSchemaName();
        String schemaUsername = tenantConnection.getSchemaUsername();
        String schemaPassword = tenantConnection.getSchemaPassword();        
        String schemaConnectionParameters = tenantConnection.getSchemaConnectionParameters();
        // Properties to ReadOnly case
        if (fineractProperties.getMode().isReadOnlyMode()) {
            schemaServer = getPropertyValue(tenantConnection.getReadOnlySchemaServer(), TenantConstants.PROPERTY_RO_SCHEMA_SERVER_NAME,
                    schemaServer);
            schemaPort = getPropertyValue(tenantConnection.getReadOnlySchemaServerPort(), TenantConstants.PROPERTY_RO_SCHEMA_SERVER_PORT,
                    schemaPort);
            schemaName = getPropertyValue(tenantConnection.getReadOnlySchemaName(), TenantConstants.PROPERTY_RO_SCHEMA_SCHEMA_NAME,
                    schemaName);
            schemaUsername = getPropertyValue(tenantConnection.getReadOnlySchemaUsername(), TenantConstants.PROPERTY_RO_SCHEMA_USERNAME,
                    schemaUsername);
            schemaPassword = getPropertyValue(tenantConnection.getReadOnlySchemaPassword(), TenantConstants.PROPERTY_RO_SCHEMA_PASSWORD,
                    schemaPassword);
            schemaConnectionParameters = getPropertyValue(tenantConnection.getReadOnlySchemaConnectionParameters(),
                    TenantConstants.PROPERTY_RO_SCHEMA_CONNECTION_PARAMETERS, schemaConnectionParameters);
        }
        String jdbcUrl = toJdbcUrl(protocol, schemaServer, schemaPort, schemaName, schemaConnectionParameters);
        logger.debug("{}", jdbcUrl);

        return jdbcUrl;
    }

    private String getPropertyValue(final String baseValue, final String propertyName, final String defaultValue) {
        // If the property already has set, return It
        if (null != baseValue) {
            return baseValue;
        }
        if (applicationContext == null) {
            return defaultValue;
        }
        return applicationContext.getEnvironment().getProperty(propertyName, defaultValue);
    }

    @Override
    public List<ReportExportType> getAvailableExportTargets() {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
}