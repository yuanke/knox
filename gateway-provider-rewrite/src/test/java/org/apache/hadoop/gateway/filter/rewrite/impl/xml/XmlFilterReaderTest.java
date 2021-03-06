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

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ExtendedBaseRules;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteStepFlow;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteCheckDescriptorExt;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteControlDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteMatchDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteMatchDescriptorExt;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteActionDescriptorBase;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmlmatchers.namespace.SimpleNamespaceContext;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.xmlmatchers.XmlMatchers.hasXPath;
import static org.xmlmatchers.transform.XmlConverters.the;

public class XmlFilterReaderTest {

  public static class NoopXmlFilterReader extends XmlFilterReader {
    public NoopXmlFilterReader( Reader reader ) throws IOException {
      super( reader );
    }

    @Override
    protected String filterText( QName elementName, String text ) {
      return text;
    }

    @Override
    protected String filterAttribute( QName elementName, QName attributeName, String attributeValue ) {
      return attributeValue;
    }
  }

  public static class MapXmlFilterReader extends XmlFilterReader {
    private Map<String,String> map;

    public MapXmlFilterReader( Reader reader, Map<String,String> map ) throws IOException {
      super( reader );
      this.map = map;
    }

    @Override
    protected String filterAttribute( QName elementName, QName attributeName, String attributeValue ) {
      return map.get( attributeValue.trim() );
    }

    @Override
    protected String filterText( QName elementName, String text ) {
      return map.get( text.trim() );
    }
  }

  @Test
  public void testSimple() throws IOException {
    String inputXml = "<root/>";
    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new NoopXmlFilterReader( inputReader );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );
    assertThat( the( outputHtml ), hasXPath( "/root" ) );
  }

  @Test
  public void testSimpleNested() throws IOException {
    String inputXml = "<root><child1><child11/><child12/></child1><child2><child21/><child22/></child2></root>";
    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new NoopXmlFilterReader( inputReader );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );
    assertThat( the( outputHtml ), hasXPath( "/root" ) );
    assertThat( the( outputHtml ), hasXPath( "/root/child1" ) );
    assertThat( the( outputHtml ), hasXPath( "/root/child1/child11" ) );
    assertThat( the( outputHtml ), hasXPath( "/root/child1/child12" ) );
    assertThat( the( outputHtml ), hasXPath( "/root/child2" ) );
    assertThat( the( outputHtml ), hasXPath( "/root/child2/child21" ) );
    assertThat( the( outputHtml ), hasXPath( "/root/child2/child22" ) );
  }

  @Test
  public void testSimpleWithNamespace() throws IOException {
    String inputXml = "<ns:root xmlns:ns='http://hortonworks.com/xml/ns'></ns:root>";
    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new NoopXmlFilterReader( inputReader );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );

    //System.out.println( outputHtml );
    SimpleNamespaceContext ns = new SimpleNamespaceContext();
    ns.bind( "ns", "http://hortonworks.com/xml/ns" );
    assertThat( the( outputHtml ), hasXPath( "/ns:root", ns ) );
  }

  @Test
  public void testSimpleTextNode() throws IOException {
    String inputXml = "<root>text</root>";
    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new NoopXmlFilterReader( inputReader );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );
    //System.out.println( outputHtml );
    assertThat( the( outputHtml ), hasXPath( "/root/text()", equalTo( "text" ) ) );
  }

  @Test
  public void testSimpleAttribute() throws IOException {
    String inputXml = "<root name='value'/>";
    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new NoopXmlFilterReader( inputReader );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );
    //System.out.println( outputHtml );
    assertThat( the( outputHtml ), hasXPath( "/root/@name", equalTo( "value" ) ) );
  }

  @Test
  public void testMappedText() throws IOException {
    Map<String,String> map = new HashMap<String,String>();
    map.put( "input-text", "output-text" );
    String inputXml = "<root>input-text</root>";
    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new MapXmlFilterReader( inputReader, map );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );
    //System.out.println( outputHtml );
    assertThat( the( outputHtml ), hasXPath( "/root/text()", equalTo( "output-text" ) ) );
  }

  @Test
  public void testMappedAttribute() throws IOException {
    Map<String,String> map = new HashMap<String,String>();
    map.put( "input-text", "output-text" );
    String inputXml = "<root attribute='input-text'/>";
    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new MapXmlFilterReader( inputReader, map );
    String outputHtml = new String( IOUtils.toCharArray( filterReader ) );
    //System.out.println( outputHtml );
    assertThat( the( outputHtml ), hasXPath( "/root/@attribute", equalTo( "output-text" ) ) );
  }

  @Test
  public void testCombined() throws IOException {
    Map<String,String> map = new HashMap<String,String>();
    map.put( "attr1-input", "attr1-output" );
    map.put( "attr2-input", "attr2-output" );
    map.put( "attr3-input", "attr3-output" );
    map.put( "attr4-input", "attr4-output" );
    map.put( "attr5-input", "attr5-output" );
    map.put( "attr6-input", "attr6-output" );
    map.put( "attr7-input", "attr7-output" );
    map.put( "root-input1", "root-output1" );
    map.put( "root-input2", "root-output2" );
    map.put( "root-input3", "root-output3" );
    map.put( "child1-input", "child1-output" );
    map.put( "child2-input", "child2-output" );

    String inputXml =
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<!-- Comment -->\n" +
            "<ns1:root xmlns:ns1='http://hortonworks.com/xml/ns1' attr1='attr1-input' ns1:attr2='attr2-input'>\n" +
            "  root-input1\n" +
            "  <child1 attr3='attr3-input' ns1:attr4='attr4-input'>\n" +
            "    child1-input\n" +
            "  </child1>\n" +
            "  root-input2\n" +
            "  <ns2:child2 xmlns:ns2='http://hortonworks.com/xml/ns2' attr5='attr5-input' ns1:attr6='attr6-input' ns2:attr7='attr7-input'>\n" +
            "    child2-input\n" +
            "  </ns2:child2>\n" +
            "  root-input3\n" +
            "</ns1:root>";
    //System.out.println( inputXml );

    StringReader inputReader = new StringReader( inputXml );
    XmlFilterReader filterReader = new MapXmlFilterReader( inputReader, map );
    String outputXml = new String( IOUtils.toCharArray( filterReader ) );
    //System.out.println( outputXml );
    //System.out.flush();

    SimpleNamespaceContext ns = new SimpleNamespaceContext();
    ns.bind( "n1", "http://hortonworks.com/xml/ns1" );
    ns.bind( "n2", "http://hortonworks.com/xml/ns2" );

    assertThat( the( outputXml ), hasXPath( "/n1:root", ns ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/@attr1", ns, equalTo( "attr1-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/@n1:attr2", ns, equalTo( "attr2-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/text()[1]", ns, equalTo( "root-output1" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/text()[2]", ns, equalTo( "root-output2" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/text()[3]", ns, equalTo( "root-output3" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/child1", ns ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/child1/@attr3", ns, equalTo( "attr3-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/child1/@n1:attr4", ns, equalTo( "attr4-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/child1/text()", ns, equalTo( "child1-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/n2:child2", ns ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/n2:child2/@attr5", ns, equalTo( "attr5-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/n2:child2/@n1:attr6", ns, equalTo( "attr6-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/n2:child2/@n2:attr7", ns, equalTo( "attr7-output" ) ) );
    assertThat( the( outputXml ), hasXPath( "/n1:root/n2:child2/text()", ns, equalTo( "child2-output" ) ) );
  }

  public static class XmlRewriteRulesDescriptorDigesterTest {

    private static DigesterLoader loader = newLoader( new XmlRewriteRulesDigester() );
    private static Digester digester = loader.newDigester( new ExtendedBaseRules() );

    @Before
    public void setupTest() {
      digester.setValidating( false );
    }

    @Test
    public void testRuleParsing() throws IOException, SAXException {
      Reader reader = new StringReader( "<rules/>" );
      UrlRewriteRulesDescriptor config = digester.parse( reader );
      assertThat( config.getRules().isEmpty(), is( true ) );

      reader = new StringReader( "<rules><rule></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().size(), is( 1 ) );
      UrlRewriteRuleDescriptor rule = config.getRules().get( 0 );
      assertThat( rule, notNullValue() );
      assertThat( rule.name(), nullValue() );
      assertThat( rule.pattern(), nullValue() );
      assertThat( rule.directions(), nullValue() );
      assertThat( rule.flow(), nullValue() );

      reader = new StringReader( "<rules><rule name=\"test-name\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule, notNullValue() );
      assertThat( rule.name(), is( "test-name" ) );
      assertThat( rule.pattern(), nullValue() );
      assertThat( rule.directions(), nullValue() );
      assertThat( rule.flow(), nullValue() );

      reader = new StringReader( "<rules><rule pattern=\"test-pattern\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule, notNullValue() );
      assertThat( rule.name(), nullValue() );
      assertThat( rule.pattern(), is( "test-pattern" ) );
      assertThat( rule.directions(), nullValue() );
      assertThat( rule.flow(), nullValue() );

      reader = new StringReader( "<rules><rule dir=\"request\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule, notNullValue() );
      assertThat( rule.name(), nullValue() );
      assertThat( rule.pattern(), nullValue() );
      assertThat( rule.directions().size(), is( 1 ) );
      assertThat( rule.directions(), contains( UrlRewriter.Direction.IN ) );
      assertThat( rule.flow(), nullValue() );

      reader = new StringReader( "<rules><rule flow=\"all\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule, notNullValue() );
      assertThat( rule.name(), nullValue() );
      assertThat( rule.pattern(), nullValue() );
      assertThat( rule.directions(), nullValue() );
      assertThat( rule.flow(), Matchers.is( UrlRewriteStepFlow.ALL ) );
    }

    @Test
    public void testDirectionParsing() throws IOException, SAXException {
      Reader reader;
      UrlRewriteRulesDescriptor config;

      reader = new StringReader( "<rules><rule dir=\"request\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"Request\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"in\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"req\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"Req\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"REQ\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"inbound\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"Inbound\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"INBOUND\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"in\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"In\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"IN\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"i\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );

      reader = new StringReader( "<rules><rule dir=\"I\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );


      reader = new StringReader( "<rules><rule dir=\"response\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"Response\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"out\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"res\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"Res\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"RES\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"outbound\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"Outbound\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"OUTBOUND\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"out\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"Out\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"OUT\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"o\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );

      reader = new StringReader( "<rules><rule dir=\"O\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.OUT ) );


      reader = new StringReader( "<rules><rule dir=\"request,response\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), hasItem( UrlRewriter.Direction.IN ) );
      assertThat( config.getRules().get( 0 ).directions(), hasItem( UrlRewriter.Direction.OUT ) );
    }

    @Test
    public void testFlowParsing() throws IOException, SAXException {
      Reader reader;
      UrlRewriteRulesDescriptor config;

      reader = new StringReader( "<rules><rule dir=\"request\"></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config.getRules().get( 0 ).directions(), contains( UrlRewriter.Direction.IN ) );
    }

    @Test
    public void testMatchParsing() throws IOException, SAXException {
      Reader reader;
      UrlRewriteRulesDescriptor config;
      UrlRewriteRuleDescriptor rule;
      UrlRewriteMatchDescriptorExt match;
      List<? extends UrlRewriteStepDescriptor> steps;

      reader = new StringReader( "<rules><rule><match></match></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config, notNullValue() );
      assertThat( config.getRules(), notNullValue() );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule.steps(), notNullValue() );
      assertThat( rule.steps().size(), is( 1 ) );
      match = (UrlRewriteMatchDescriptorExt)rule.steps().get( 0 );
      assertThat( match, notNullValue() );
      //assertThat( match.type(), nullValue() );
      assertThat( match.operation(), nullValue() );
      assertThat( match.pattern(), nullValue() );

      reader = new StringReader( "<rules><rule><match type=\"test-type\" op=\"test-op\" pattern=\"test-pattern\"></match></rule></rules>" );
      config = digester.parse( reader );
      match = (UrlRewriteMatchDescriptorExt)config.getRules().get( 0 ).steps().get( 0 );
      //assertThat( match.type(), is("test-type") );
      assertThat( match.operation(), is( "test-op" ) );
      assertThat( match.pattern(), is( "test-pattern" ) );

      reader = new StringReader( "<rules><rule name=\"test\"><match><match pattern=\"test-pattern\"></match></match></rule></rules>" );
      config = digester.parse( reader );
      steps = ((UrlRewriteMatchDescriptor)config.getRule( "test" ).steps().get( 0 )).steps();
      assertThat( steps, notNullValue() );
      assertThat( steps.size(), is( 1 ) );
      assertThat( steps.get( 0 ), notNullValue() );
      match = (UrlRewriteMatchDescriptorExt)steps.get( 0 );
      assertThat( match.pattern(), is( "test-pattern" ) );
    }

    @Test
    public void testCheckParsing() throws IOException, SAXException {
      Reader reader;
      UrlRewriteRulesDescriptor config;
      UrlRewriteRuleDescriptor rule;
      List<UrlRewriteStepDescriptor> steps;
      UrlRewriteCheckDescriptorExt step;

      reader = new StringReader( "<rules><rule><check></check></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config, notNullValue() );
      assertThat( config.getRules(), notNullValue() );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule.steps(), notNullValue() );
      assertThat( rule.steps().size(), is( 1 ) );
      step = (UrlRewriteCheckDescriptorExt)rule.steps().get( 0 );
      assertThat( step, notNullValue() );
      //assertThat( step.type(), nullValue() );
      assertThat( step.operation(), nullValue() );
      assertThat( step.input(), nullValue() );
      assertThat( step.value(), nullValue() );

      reader = new StringReader( "<rules><rule><check type=\"test-type\" op=\"test-op\" input=\"test-input\" value=\"test-value\"></check></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config, notNullValue() );
      assertThat( config.getRules(), notNullValue() );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule.steps(), notNullValue() );
      assertThat( rule.steps().size(), is( 1 ) );
      step = (UrlRewriteCheckDescriptorExt)rule.steps().get( 0 );
      assertThat( step, notNullValue() );
      //assertThat( step.type(), is( "test-type" ) );
      assertThat( step.operation(), is( "test-op" ) );
      assertThat( step.input(), is( "test-input" ) );
      assertThat( step.value(), is( "test-value" ) );
    }

    @Test
    public void testActionParsing() throws IOException, SAXException {
      Reader reader;
      UrlRewriteRulesDescriptor config;
      UrlRewriteRuleDescriptor rule;
      UrlRewriteActionDescriptorBase step;

      reader = new StringReader( "<rules><rule><action></action></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config, notNullValue() );
      assertThat( config.getRules(), notNullValue() );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule.steps(), notNullValue() );
      assertThat( rule.steps().size(), is( 1 ) );
      step = (UrlRewriteActionDescriptorBase)rule.steps().get( 0 );
      assertThat( step, notNullValue() );
      //assertThat( step.type(), nullValue() );
      assertThat( step.parameter(), nullValue() );

      reader = new StringReader( "<rules><rule><action type=\"test-type\" param=\"test-param\"></action></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config, notNullValue() );
      assertThat( config.getRules(), notNullValue() );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule.steps(), notNullValue() );
      assertThat( rule.steps().size(), is( 1 ) );
      step = (UrlRewriteActionDescriptorBase)rule.steps().get( 0 );
      assertThat( step, notNullValue() );
      //assertThat( step.type(), is( "test-type" ) );
      assertThat( step.parameter(), is( "test-param" ) );
    }

    @Test
    public void testControlParsing() throws IOException, SAXException {
      Reader reader;
      UrlRewriteRulesDescriptor config;
      UrlRewriteRuleDescriptor rule;
      List<UrlRewriteStepDescriptor> steps;

      reader = new StringReader( "<rules><rule><control></control></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config, notNullValue() );
      assertThat( config.getRules(), notNullValue() );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule.steps(), notNullValue() );
      assertThat( rule.steps().size(), is( 1 ) );
      UrlRewriteControlDescriptor step = (UrlRewriteControlDescriptor)rule.steps().get( 0 );
      assertThat( step, notNullValue() );
      assertThat(  step.flow(), nullValue() );

      reader = new StringReader( "<rules><rule><control flow=\"or\"></control></rule></rules>" );
      config = digester.parse( reader );
      assertThat( config, notNullValue() );
      assertThat( config.getRules(), notNullValue() );
      assertThat( config.getRules().size(), is( 1 ) );
      rule = config.getRules().get( 0 );
      assertThat( rule.steps(), notNullValue() );
      assertThat( rule.steps().size(), is( 1 ) );
      step = (UrlRewriteControlDescriptor)rule.steps().get( 0 );
      assertThat( step, notNullValue() );
      assertThat( step.flow(), is( UrlRewriteStepFlow.OR ) );
    }
  }
}
