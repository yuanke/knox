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
package org.apache.hadoop.gateway.filter.rewrite.impl.xml;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.hadoop.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;

import static org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter.Direction;

public class XmlUrlRewriteFilterReader extends XmlFilterReader {

  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private Resolver resolver;
  private UrlRewriter rewriter;
  private Direction direction;

  public XmlUrlRewriteFilterReader( Reader reader, UrlRewriter rewriter, Resolver resolver, Direction direction )
      throws IOException {
    super( reader );
    this.resolver = resolver;
    this.rewriter = rewriter;
    this.direction = direction;
  }

  //TODO: Need to limit which values are attempted to be filtered by the name.
  private String filterValueString( String name, String value ) {
    try {
      Template input = Parser.parse( value );
      Template output = rewriter.rewrite( resolver, input, direction );
      value = output.toString();
    } catch( URISyntaxException e ) {
      LOG.failedToParseValueForUrlRewrite( value );
    }
    return value;
  }

  @Override
  protected String filterAttribute( QName elementName, QName attributeName, String attributeValue ) {
    return filterValueString( attributeName.getLocalPart(), attributeValue );
  }

  @Override
  protected String filterText( QName elementName, String text ) {
    return filterValueString( elementName.getLocalPart(), text );
  }
}
