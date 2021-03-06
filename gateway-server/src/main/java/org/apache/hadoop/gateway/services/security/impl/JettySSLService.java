/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.services.security.impl;

import java.io.File;
import java.util.Map;

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.services.security.MasterService;
import org.apache.hadoop.gateway.services.security.SSLService;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettySSLService implements SSLService {
  private static final String GATEWAY_IDENTITY_PASSPHRASE = "gateway-identity-passphrase";
  private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";
  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  
  private MasterService ms;
  private KeystoreService ks;
  private AliasService as;

  public void setMasterService(MasterService ms) {
    this.ms = ms;
  }

  public void setAliasService(AliasService as) {
    this.as = as;
  }

  public void setKeystoreService(KeystoreService ks) {
    this.ks = ks;
  }

  
  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    try {
      if (!ks.isCredentialStoreForClusterAvailable(GATEWAY_CREDENTIAL_STORE_NAME)) {
        log.creatingCredentialStoreForGateway();
        ks.createCredentialStoreForCluster(GATEWAY_CREDENTIAL_STORE_NAME);
        as.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
      }
      else {
        log.credentialStoreForGatewayFoundNotCreating();
      }
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
    }

    try {
      if (!ks.isKeystoreForGatewayAvailable()) {
        log.creatingKeyStoreForGateway();
        ks.createKeystoreForGateway();
        char[] passphrase = as.getPasswordFromAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
        ks.addSelfSignedCertForGateway("gateway-identity", passphrase);
      }
      else {
        log.keyStoreForGatewayFoundNotCreating();
      }
    } catch (KeystoreServiceException e) {
      throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
    }
  }
  
  public Object buildSSlConnector(String gatewayHomeDir) {
    SslContextFactory sslContextFactory = new SslContextFactory( true );
    sslContextFactory.setCertAlias( "gateway-identity" );
    String keystorePath = gatewayHomeDir + File.separatorChar +  "conf" + File.separatorChar +  "security" + File.separatorChar + "keystores" + File.separatorChar + "gateway.jks";
    sslContextFactory.setKeyStoreType("JKS");
    sslContextFactory.setKeyStorePath(keystorePath);
    char[] master = ms.getMasterSecret();
    sslContextFactory.setKeyStorePassword(new String(master));
    char[] keypass = as.getPasswordFromAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
    sslContextFactory.setKeyManagerPassword(new String(keypass));

    // TODO: make specific truststore too?
//    sslContextFactory.setTrustStore(keystorePath);
//    sslContextFactory.setTrustStorePassword(new String(keypass));
    sslContextFactory.setNeedClientAuth( false );
    sslContextFactory.setTrustAll( true );
    SslConnector sslConnector = new SslSelectChannelConnector( sslContextFactory );

    return sslConnector;
  }  
  
  @Override
  public void start() throws ServiceLifecycleException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    // TODO Auto-generated method stub
    
  }
}
