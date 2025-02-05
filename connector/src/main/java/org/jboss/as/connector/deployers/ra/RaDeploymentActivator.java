/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.deployers.ra;

import static org.jboss.as.connector.util.ConnectorServices.TRANSACTION_INTEGRATION_CAPABILITY_NAME;

import org.jboss.as.connector.deployers.ds.processors.DriverProcessor;
import org.jboss.as.connector.deployers.ds.processors.DriverManagerAdapterProcessor;
import org.jboss.as.connector.deployers.ds.processors.StructureDriverProcessor;
import org.jboss.as.connector.deployers.ra.processors.IronJacamarDeploymentParsingProcessor;
import org.jboss.as.connector.deployers.ra.processors.ParsedRaDeploymentProcessor;
import org.jboss.as.connector.deployers.ra.processors.RaDeploymentParsingProcessor;
import org.jboss.as.connector.deployers.ra.processors.RaNativeProcessor;
import org.jboss.as.connector.deployers.ra.processors.RaStructureProcessor;
import org.jboss.as.connector.deployers.ra.processors.RaXmlDependencyProcessor;
import org.jboss.as.connector.deployers.ra.processors.RaXmlDeploymentProcessor;
import org.jboss.as.connector.deployers.ra.processors.RarDependencyProcessor;
import org.jboss.as.connector.services.mdr.MdrService;
import org.jboss.as.connector.services.rarepository.NonJTADataSourceRaRepositoryService;
import org.jboss.as.connector.services.rarepository.RaRepositoryService;
import org.jboss.as.connector.services.resourceadapters.deployment.registry.ResourceAdapterDeploymentRegistryService;
import org.jboss.as.connector.services.resourceadapters.repository.ManagementRepositoryService;
import org.jboss.as.connector.subsystems.resourceadapters.ResourceAdaptersExtension;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.jca.core.spi.mdr.MetadataRepository;
import org.jboss.jca.core.spi.transaction.TransactionIntegration;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;

/**
 * Service activator which installs the various service required for rar
 * deployments.
 *
 * @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 */
public class RaDeploymentActivator {

    private final boolean appclient;
    private final boolean legacySecurityAvailable;
    private final MdrService mdrService = new MdrService();

    public RaDeploymentActivator(final boolean appclient, final boolean legacySecurityAvailable) {
        this.appclient = appclient;
        this.legacySecurityAvailable = legacySecurityAvailable;
    }

    public void activateServices(final ServiceTarget serviceTarget) {
        // add resources here

        serviceTarget.addService(ConnectorServices.IRONJACAMAR_MDR, mdrService)
                .install();

        RaRepositoryService raRepositoryService = new RaRepositoryService();
        serviceTarget.addService(ConnectorServices.RA_REPOSITORY_SERVICE, raRepositoryService)
                .addDependency(ConnectorServices.IRONJACAMAR_MDR, MetadataRepository.class, raRepositoryService.getMdrInjector())
                .addDependency(ConnectorServices.getCachedCapabilityServiceName(TRANSACTION_INTEGRATION_CAPABILITY_NAME), TransactionIntegration.class,
                        raRepositoryService.getTransactionIntegrationInjector())
                .install();

        // Special resource adapter repository and bootstrap context for non-JTA datasources

        NonJTADataSourceRaRepositoryService nonJTADataSourceRaRepositoryService = new NonJTADataSourceRaRepositoryService();
        serviceTarget.addService(ConnectorServices.NON_JTA_DS_RA_REPOSITORY_SERVICE, nonJTADataSourceRaRepositoryService)
                .addDependency(ConnectorServices.IRONJACAMAR_MDR, MetadataRepository.class, nonJTADataSourceRaRepositoryService.getMdrInjector())
                .install();

        ManagementRepositoryService managementRepositoryService = new ManagementRepositoryService();
        serviceTarget.addService(ConnectorServices.MANAGEMENT_REPOSITORY_SERVICE, managementRepositoryService)
                .install();

        ResourceAdapterDeploymentRegistryService registryService = new ResourceAdapterDeploymentRegistryService();
        final ServiceBuilder sb = serviceTarget.addService(ConnectorServices.RESOURCE_ADAPTER_REGISTRY_SERVICE, registryService);
        sb.requires(ConnectorServices.IRONJACAMAR_MDR);
        sb.install();
    }


    public void activateProcessors(final DeploymentProcessorTarget updateContext) {
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_RAR, new RaStructureProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_JDBC_DRIVER, new StructureDriverProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_RA_DEPLOYMENT, new RaDeploymentParsingProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_IRON_JACAMAR_DEPLOYMENT,
                new IronJacamarDeploymentParsingProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_RESOURCE_DEF_ANNOTATION_CONNECTION_FACTORY,
                new ConnectionFactoryDefinitionAnnotationProcessor(legacySecurityAvailable));
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_RESOURCE_DEF_ANNOTATION_ADMINISTERED_OBJECT,
                new AdministeredObjectDefinitionAnnotationProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_RAR_CONFIG, new RarDependencyProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.CONFIGURE_MODULE, Phase.CONFIGURE_JDBC_DRIVER_MANAGER_ADAPTER, new DriverManagerAdapterProcessor());
        if (!appclient)
            updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_RAR_SERVICES_DEPS, new RaXmlDependencyProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_RESOURCE_DEF_XML_CONNECTION_FACTORY,
                new ConnectionFactoryDefinitionDescriptorProcessor(legacySecurityAvailable));
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_RESOURCE_DEF_XML_ADMINISTERED_OBJECT,
                new AdministeredObjectDefinitionDescriptorProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_RA_NATIVE, new RaNativeProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_RA_DEPLOYMENT, new ParsedRaDeploymentProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_RA_XML_DEPLOYMENT, new RaXmlDeploymentProcessor());
        updateContext.addDeploymentProcessor(ResourceAdaptersExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_JDBC_DRIVER, new DriverProcessor());
    }
}
