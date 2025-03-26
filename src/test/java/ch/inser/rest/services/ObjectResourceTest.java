/*
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package ch.inser.rest.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.junit.Before;
import org.junit.Test;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.common.IValueObject.Type;
import ch.inser.dynaplus.vo.GenericValueObject;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.GenericContextManager;
import ch.inser.rest.util.JsonVoUtil;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.ServletContext;

/**
 * Tests unitaires sur la consultation et update d'un objet métier
 *
 * @author INSER SA *
 */
public class ObjectResourceTest {

    /**
     * Mocked object
     */
    ServletContext iContext;

    /**
     * Mocked object
     */
    VOFactory iVOFactory;

    /**
     * Generic value object
     */
    IValueObject iValueObject;

    /**
     * Context manager
     */
    IContextManager iContextManager;

    /**
     * Initialisation des mock
     */
    @Before
    public void initMock() {
        initContextMock();
    }

    /**
     * initialisation du mock context
     */
    private void initContextMock() {
        iContext = mock(ServletContext.class);
        iVOFactory = mock(VOFactory.class);
        iContextManager = mock(GenericContextManager.class);

        when(iContext.getAttribute("VOFactory")).thenReturn(iVOFactory);
        JsonVoUtil.setContextManager(iContextManager);

    }

    /**
     * Init un value object avec des noms de champs et leurs types
     *
     * @param aFilename
     *            nom du fichier qui contient les champs et leurs types
     * @throws IOException
     *             erreur de lecture du fichier
     */
    private void initValueObject(String aFilename) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(aFilename)))) {
            Map<String, Type> types = new HashMap<>();
            String line;
            while ((line = br.readLine()) != null) {
                String field = line.substring(0, line.indexOf(':'));
                String type = line.substring(line.indexOf(':') + 1, line.length());
                types.put(field, getType(type));
            }
            iValueObject = new GenericValueObject(null, null, types);
            when(iVOFactory.getVO(anyString())).thenReturn(iValueObject);
        }
    }

    /**
     *
     * @param aType
     *            en type en forme string
     * @return l'énumeration Type
     */
    private Type getType(String aType) {
        for (Type type : Type.values()) {
            if (type.toString().equalsIgnoreCase(aType)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Test de base sur les transformations de JSON en ValueObject. Exemple avec des champs de type:
     *
     * String, Boolean, Long, Double, Date et Timestamp.
     *
     *
     * @throws IOException
     *             erreur de lecture de fichier avec les données de test
     * @throws ISException
     *             on database access problems
     */
    @Test
    public void testJsonToVo() throws IOException, ISException {
        initValueObject(getPath("ch/inser/rest/services/person1-attributes.txt"));
        try (Scanner scan = new Scanner(new File(getPath("ch/inser/rest/services/person1-json.txt")))) {
            String json = scan.useDelimiter("\\Z").next();
            IValueObject vo = JsonVoUtil.jsonToVo(json, ((VOFactory) iContext.getAttribute("VOFactory")).getVO((String) null));
            JsonObject backToJson = JsonVoUtil.voToJson(vo, false, null);
            try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
                JsonObject initial = jsonReader.readObject();
                for (String field : initial.keySet()) {
                    assertSame("Tous les attributs ont été pris", initial.keySet().size(), backToJson.keySet().size());
                    assertEquals(
                            "Json initiale correspond au json reconstitué depuis le VO. Field: " + field + ". Initial: "
                                    + initial.get(field) + ". Reconstitué: " + backToJson.get(field),
                            initial.get(field).toString(), backToJson.get(field).toString());
                }
            }
        }
    }

    /**
     *
     * @param aFilename
     *            nom du fichier à partir de la racine du projet
     * @return le path absolu
     */
    private String getPath(String aFilename) {
        return Thread.currentThread().getContextClassLoader().getResource(aFilename).getPath();
    }

}
