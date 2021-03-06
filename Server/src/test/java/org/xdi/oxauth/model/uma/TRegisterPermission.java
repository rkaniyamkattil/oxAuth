/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.model.uma;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.xdi.oxauth.BaseTest;
import org.xdi.oxauth.model.common.Holder;
import org.xdi.oxauth.model.uma.wrapper.Token;
import org.xdi.oxauth.util.ServerUtil;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;

import static org.testng.Assert.*;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 15/03/2013
 */

class TRegisterPermission {

	private final URI baseUri;

	public TRegisterPermission(URI baseUri) {
		assertNotNull(baseUri); // must not be null
		this.baseUri = baseUri;
	}

	public PermissionTicket registerPermission(final Token p_pat, final UmaPermission p_request, String path) {
		final Holder<PermissionTicket> ticketH = new Holder<PermissionTicket>();
		Builder request = ResteasyClientBuilder.newClient().target(baseUri.toString() + path).request();
		request.header("Accept", UmaConstants.JSON_MEDIA_TYPE);
		request.header("Authorization", "Bearer " + p_pat.getAccessToken());

		String json = null;
		try {
			json = ServerUtil.createJsonMapper().writeValueAsString(p_request);
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}

		Response response = request.post(Entity.json(json));
		String entity = response.readEntity(String.class);

		BaseTest.showResponse("UMA : TRegisterPermission.registerPermission() : ", response, entity);

		assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode(), "Unexpected response code.");
		try {
			final PermissionTicket t = ServerUtil.createJsonMapper().readValue(entity, PermissionTicket.class);
			UmaTestUtil.assert_(t);

			ticketH.setT(t);
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}

		return ticketH.getT();
	}
}
