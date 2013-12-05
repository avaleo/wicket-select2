/*
 * Copyright 2012 Igor Vaynberg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with
 * the License. You may obtain a copy of the License in the LICENSE file, or at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.vaynberg.wicket.select2;

import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.wicket.IResourceListener;
import org.apache.wicket.Request;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.form.HiddenField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.util.string.StringValue;
import org.json.JSONException;
import org.json.JSONWriter;

/**
 * Base class for Select2 components
 * 
 * @author igor
 * 
 * @param <T>
 *            type of choice object
 * @param <M>
 *            type of model object
 */
abstract class AbstractSelect2Choice<T, M> extends HiddenField<M> implements IResourceListener, IHeaderContributor {

    private static final String WEB_REQUEST_PARAM_AJAX = "wicket-ajax"; //WebRequest.PARAM_AJAX 
    private static final String WEB_REQUEST_PARAM_AJAX_BASE_URL = "wicket-ajax-baseurl"; //WebRequest.PARAM_AJAX_BASE_URL

    private static final long serialVersionUID = 1L;

    private final Settings settings = new Settings();

    private ChoiceProvider<T> provider;

    /**
     * Constructor
     * 
     * @param id
     *            component id
     * @param model
     *            component model
     * @param provider
     *            choice provider
     */
    public AbstractSelect2Choice(String id) {
	this(id, null, null);
    }

    /**
     * Constructor
     * 
     * @param id
     *            component id
     * @param model
     *            component model
     */
    public AbstractSelect2Choice(String id, IModel<M> model) {
	this(id, model, null);
    }

    /**
     * Constructor.
     *
     * @param id
     *            component id
     * @param provider
     *            choice provider
     */
    public AbstractSelect2Choice(String id, ChoiceProvider<T> provider) {
    this(id, null, provider);
    }

    /**
     * Constructor
     *
     * @param id
     *            component id
     * @param model
     *            component model
     * @param provider
     *            choice provider
     */
    public AbstractSelect2Choice(String id, IModel<M> model, ChoiceProvider<T> provider) {
	super(id, model);
	this.provider = provider;

	setOutputMarkupId(true);
    }

    /**
     * @return Select2 settings for this component
     */
    public final Settings getSettings() {
	return settings;
    }

    /**
     * Sets the choice provider
     * 
     * @param provider
     */
    public final void setProvider(ChoiceProvider<T> provider) {
	this.provider = provider;
    }

    /**
     * @return choice provider
     */
    public final ChoiceProvider<T> getProvider() {
	if (provider == null) {
	    throw new IllegalStateException("Select2 choice component: " + getId()
		    + " does not have a ChoiceProvider set");
	}
	return provider;
    }

    /**
     * Gets the markup id that is safe to use in jQuery by escaping dots in the default {@link #getMarkup()}
     * 
     * @return markup id
     */
    protected String getJquerySafeMarkupId() {
	return getMarkupId().replace(".", "\\\\.");
    }

    @Override
    public void renderHead(IHeaderResponse response) {
	AjaxRequestTarget target = AjaxRequestTarget.get(); 
        if (target != null) {
            if (target.getComponents().contains(this)) {
        	// if this component is being repainted by ajax, directly, we must destroy Select2 so it removes
        	// its elements from DOM
        	response.renderJavascript(JQuery.execute("$('#%s').select2('destroy');", getJquerySafeMarkupId()), null);
            }
        }
	
	// initialize select2
	new Select2ResourcesBehavior().renderHead(response);

	response.renderOnDomReadyJavascript(JQuery.execute("$('#%s').select2(%s);", getJquerySafeMarkupId(),
            settings.toJson()));

	// select current value

	renderInitializationScript(response);
    }

    /**
     * Renders script used to initialize the value of Select2 after it is created so it matches the current model
     * object.
     * 
     * @param response
     *            header response
     */
    protected abstract void renderInitializationScript(IHeaderResponse response);

    @Override
    protected void onInitialize() {
	super.onInitialize();

	// configure the ajax callbacks

	AjaxSettings ajax = settings.getAjax(true);

	ajax.setData(String
		.format("function(term, page) { return { term: term, page:page, '%s':true, '%s':[window.location.protocol, '//', window.location.host, window.location.pathname].join('')}; }",
			WEB_REQUEST_PARAM_AJAX, WEB_REQUEST_PARAM_AJAX_BASE_URL));

	ajax.setResults("function(data, page) { return data; }");
    }

    @Override
    protected void onConfigure() {
	super.onConfigure();

	getSettings().getAjax().setUrl(urlFor(IResourceListener.INTERFACE));
    }
    
    @Override
    public void onResourceRequested() {

	// this is the callback that retrieves matching choices used to populate the dropdown

	RequestCycle rc = RequestCycle.get();
	Request request = rc.getRequest();
	
	// retrieve choices matching the search term

	String term = StringValue.valueOf(request.getParameter("term")).toOptionalString();

	int page = StringValue.valueOf(request.getParameter("page")).toInt(1);
	// select2 uses 1-based paging, but in wicket world we are used to
	// 0-based
	page -= 1;

	Response<T> response = new Response<T>();
	provider.query(term, page, response);

	// jsonize and write out the choices to the response

	WebResponse webResponse = (WebResponse) rc.getResponse();
	webResponse.setContentType("application/json");

	OutputStreamWriter out = new OutputStreamWriter(webResponse.getOutputStream(), CharsetExtractor.extractCharset(request));
	JSONWriter json = new JSONWriter(out);

	try {
	    json.object();
	    json.key("results").array();
	    for (T item : response) {
		json.object();
		provider.toJson(item, json);
		json.endObject();
	    }
	    json.endArray();
	    json.key("more").value(response.getHasMore()).endObject();
	} catch (JSONException e) {
	    throw new RuntimeException("Could not write Json response", e);
	}

	try {
	    out.flush();
	} catch (IOException e) {
	    throw new RuntimeException("Could not write Json to servlet response", e);
	}
    }

    @Override
    protected void onDetach() {
	provider.detach();
	super.onDetach();
    }
}
