package org.apache.tika.parser;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import static org.apache.tika.TikaTest.assertContains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.utils.CommonsDigester;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

public class RecursiveParserWrapperTest {

    @Test
    public void testBasicXML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.indexOf("<p class=\"header\" />") > -1);
    }

    @Test
    public void testBasicHTML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.HTML, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.indexOf("<p class=\"header\"></p>") > -1);
    }

    @Test
    public void testBasicText() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        assertTrue(content.indexOf("<p ") < 0);
        assertTrue(content.indexOf("embed_0") > -1);
    }

    @Test
    public void testIgnoreContent() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        assertNull(content);
    }


    @Test
    public void testCharLimit() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();

        Parser wrapped = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(wrapped,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, 60));
        InputStream stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        List<Metadata> list = wrapper.getMetadata();

        assertEquals(5, list.size());

        int wlr = 0;
        for (Metadata m : list) {
            String limitReached = m.get(RecursiveParserWrapper.WRITE_LIMIT_REACHED);
            if (limitReached != null && limitReached.equals("true")) {
                wlr++;
            }
        }
        assertEquals(1, wlr);

    }

    @Test
    public void testMaxEmbedded() throws Exception {
        int maxEmbedded = 4;
        int totalNoLimit = 12;//including outer container file
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String limitReached = null;

        Parser wrapped = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(wrapped,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));

        InputStream stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        List<Metadata> list = wrapper.getMetadata();
        //test default
        assertEquals(totalNoLimit, list.size());

        limitReached = list.get(0).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertNull(limitReached);


        wrapper.reset();
        stream.close();

        //test setting value
        metadata = new Metadata();
        stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        wrapper.setMaxEmbeddedResources(maxEmbedded);
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        list = wrapper.getMetadata();

        //add 1 for outer container file
        assertEquals(maxEmbedded + 1, list.size());

        limitReached = list.get(0).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertEquals("true", limitReached);

        wrapper.reset();
        stream.close();

        //test setting value < 0
        metadata = new Metadata();
        stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");

        wrapper.setMaxEmbeddedResources(-2);
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        assertEquals(totalNoLimit, list.size());
        limitReached = list.get(0).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertNull(limitReached);
    }

    @Test
    public void testEmbeddedResourcePath() throws Exception {

        Set<String> targets = new HashSet<String>();
        targets.add("/embed1.zip");
        targets.add("/embed1.zip/embed2.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed4.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed4.zip/embed4.txt");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed3.txt");
        targets.add("/embed1.zip/embed2.zip/embed2a.txt");
        targets.add("/embed1.zip/embed2.zip/embed2b.txt");
        targets.add("/embed1.zip/embed1b.txt");
        targets.add("/embed1.zip/embed1a.txt");
        targets.add("/image1.emf");

        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        assertTrue(content.indexOf("<p class=\"header\" />") > -1);

        Set<String> seen = new HashSet<String>();
        for (Metadata m : list) {
            String path = m.get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH);
            if (path != null) {
                seen.add(path);
            }
        }
        assertEquals(targets, seen);
    }

    @Test
    public void testEmbeddedNPE() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test_recursive_embedded_npe.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        //default behavior (user doesn't specify whether or not to catch embedded exceptions
        //is to catch the exception
        assertEquals(13, list.size());
        Metadata mockNPEMetadata = list.get(10);
        assertContains("java.lang.NullPointerException", mockNPEMetadata.get(RecursiveParserWrapper.EMBEDDED_EXCEPTION));

        metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test_recursive_embedded_npe.docx");
        list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                false, null);

        //Composite parser swallows caught TikaExceptions, IOExceptions and SAXExceptions
        //and just doesn't bother to report that there was an exception.
        assertEquals(12, list.size());
    }

    @Test
    public void testPrimaryExcWEmbedded() throws Exception {
        //if embedded content is handled and then
        //the parser hits an exception in the container document,
        //that the first element of the returned list is the container document
        //and the second is the embedded content
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "embedded_then_npe.xml");

        ParseContext context = new ParseContext();
        Parser wrapped = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(wrapped,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1), true);
        String path = "/test-documents/mock/embedded_then_npe.xml";

        InputStream stream = null;
        boolean npe = false;
        try {
            stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                    path);
            wrapper.parse(stream, new DefaultHandler(), metadata, context);
        } catch (TikaException e) {
            if (e.getCause().getClass().equals(NullPointerException.class)) {
                npe = true;
            }
        } finally {
            IOUtils.closeQuietly(stream);
        }
        assertTrue("npe", npe);

        List<Metadata> metadataList = wrapper.getMetadata();
        assertEquals(2, metadataList.size());
        Metadata outerMetadata = metadataList.get(0);
        Metadata embeddedMetadata = metadataList.get(1);
        assertContains("main_content", outerMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("embedded_then_npe.xml", outerMetadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY));
        assertEquals("Nikolai Lobachevsky", outerMetadata.get("author"));

        assertContains("some_embedded_content", embeddedMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("embed1.xml", embeddedMetadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY));
        assertEquals("embeddedAuthor", embeddedMetadata.get("author"));
    }

    @Test
    public void testDigesters() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                true, new CommonsDigester(100000, CommonsDigester.DigestAlgorithm.MD5));
        int i = 0;
        Metadata m0 = list.get(0);
        Metadata m6 = list.get(6);
        String md5Key = "X-TIKA:digest:MD5";
        assertEquals("59f626e09a8c16ab6dbc2800c685f772", list.get(0).get(md5Key));
        assertEquals("ccdf3882e7e4c2454e28884db9b0a54d", list.get(6).get(md5Key));
        assertEquals("a869bf6432ebd14e19fc79416274e0c9", list.get(7).get(md5Key));
    }

    private List<Metadata> getMetadata(Metadata metadata, ContentHandlerFactory contentHandlerFactory,
                                       boolean catchEmbeddedExceptions,
                                       DigestingParser.Digester digester) throws Exception {
        ParseContext context = new ParseContext();
        Parser wrapped = new AutoDetectParser();
        if (digester != null) {
            wrapped = new DigestingParser(wrapped, digester);
        }
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(wrapped,
                contentHandlerFactory, catchEmbeddedExceptions);
        String path = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (path == null) {
            path = "/test-documents/test_recursive_embedded.docx";
        } else {
            path = "/test-documents/" + path;
        }
        InputStream stream = null;
        try {
            stream = TikaInputStream.get(RecursiveParserWrapperTest.class.getResource(path).toURI());
            wrapper.parse(stream, new DefaultHandler(), metadata, context);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        return wrapper.getMetadata();

    }

    private List<Metadata> getMetadata(Metadata metadata, ContentHandlerFactory contentHandlerFactory)
            throws Exception {
        return getMetadata(metadata, contentHandlerFactory, true, null);
    }
}