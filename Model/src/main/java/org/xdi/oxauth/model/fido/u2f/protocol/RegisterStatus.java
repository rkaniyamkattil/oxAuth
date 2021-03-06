/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2015, Gluu
 */

package org.xdi.oxauth.model.fido.u2f.protocol;

import java.io.Serializable;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.xdi.oxauth.model.fido.u2f.exception.BadInputException;

/**
 * FIDO U2F device registration status response
 *
 * @author Yuriy Movchan Date: 05/20/2015
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterStatus implements Serializable {

	private static final long serialVersionUID = -1113719742487477953L;

	@JsonProperty
	private final String status;

	@JsonProperty
	private final String challenge;

	public RegisterStatus(@JsonProperty("status") String status, @JsonProperty("challenge") String challenge) throws BadInputException {
		this.status = status;
		this.challenge = challenge;
	}

	public String getStatus() {
		return status;
	}

	public String getChallenge() {
		return challenge;
	}

	@JsonIgnore
	public String getRequestId() {
		return challenge;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RegisterStatus [status=").append(status).append(", challenge=").append(challenge).append("]");
		return builder.toString();
	}

}
