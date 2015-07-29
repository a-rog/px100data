/*
 * This file is part of Px100 Data.
 *
 * Px100 Data is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package com.px100systems.data.browser.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import com.px100systems.data.core.StoredBean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.px100systems.data.core.Criteria;
import com.px100systems.data.core.DatabaseStorage;
import com.px100systems.data.core.Entity;
import com.px100systems.data.core.Transaction;
import com.px100systems.util.ELTemplateParserContext;
import com.px100systems.util.SpringELCtx;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * DB Browser main page controller
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@Controller
public class MainController {
	private static Log log = LogFactory.getLog(MainController.class);
	
	@SuppressWarnings("SpringJavaAutowiringInspection")
	@Resource(name="clusterName")
	private String clusterName;

	@SuppressWarnings("SpringJavaAutowiringInspection")
	@Resource(name="dataStorage")
	private DatabaseStorage dataStorage;
	
	@Autowired
	private Encoder passwordEncoder;
	
	@Autowired
	private DbBrowserUserDetailsService authenticationProvider;
	
	@Autowired
    private AuthenticationManager authenticationManager;
	
	private static final String USER_ATTRIBUTE = "USER_NAME";
	private static final String JSON_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
	
	private static final int MAX_ROWS = 1000;
	
	public MainController() {
	}
	
	public void autoLogin(HttpServletRequest request, String username, String password) {
		List<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
		authorities.add(new SimpleGrantedAuthority(DbBrowserUserDetailsService.DEFAULT_AUTHORITY));
		
		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username, password, authorities);
	    request.getSession();
	    token.setDetails(new WebAuthenticationDetails(request));
	    Authentication authenticatedUser = authenticationManager.authenticate(token);
	    SecurityContextHolder.getContext().setAuthentication(authenticatedUser);
		HttpSession session = request.getSession();
	    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());			
		session.setAttribute(USER_ATTRIBUTE, username);
	}

	public String setUserPassword(String user, String oldPassword, String newPassword) {
		try {
			Properties users = authenticationProvider.getUsers();
			
			if (newPassword == null || newPassword.trim().isEmpty())
				return "Empty password";
	
			if (!users.containsKey(user))
				return "User not found: " + user;
	
			String oldPass = users.getProperty(user);
			if (oldPass != null && !oldPass.isEmpty() && (oldPassword == null || !passwordEncoder.matches(oldPassword, oldPass)))
				return "Old password mismatch";
	
			users.setProperty(user, passwordEncoder.encode(newPassword.trim()));
			authenticationProvider.saveUsers(users);
			return null;
		} catch (Exception e) {
			return e.getMessage();
		}
	}
	
	@RequestMapping(value="/register/{user}")
	public ModelAndView register(@PathVariable String user) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("activate");
		mav.addObject("user", user);

		String password = authenticationProvider.getUsers().getProperty(user);
		if (password == null) {
			mav.setViewName(ERROR_PAGE);
			mav.addObject("error", "User not found: " + user);
			return mav;
		} 

		mav.setViewName(password.isEmpty() ? "activate" : "login");
		mav.addObject("cluster", clusterName);
		return mav;
	}

	@RequestMapping(value="/login")
	public ModelAndView login() {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("login");
		mav.addObject("cluster", clusterName);
		return mav;
	}

    @RequestMapping("/activate")
	public ModelAndView activate(HttpServletRequest request, HttpServletResponse response,  
			@RequestParam(value="user") String user,
			@RequestParam(value="password") String password,
			@RequestParam(value="password2") String password2) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("activate");
 
        if (password == null || password2 == null || password.trim().isEmpty() || !password.equals(password2)) {
    		mav.addObject("error", "Invalid or mismatched password");
    		return mav;
        }
        
        String error = setUserPassword(user, null, password);
        if (error != null) {
    		mav.addObject("error", error);
    		return mav;
        }
        
		try {
			autoLogin(request, user, password);
			response.sendRedirect(request.getContextPath() + "/browse");
			return null;
		} catch (Exception e) {
    		mav.addObject("error", e.getMessage());
	        return mav;
		}
	}

	@RequestMapping(value="/password/change")
	public ModelAndView passwordPage(HttpServletRequest request) {
        ModelAndView mav = new ModelAndView();
		mav.setViewName("password");
		
		HttpSession session = request.getSession(false);
		if (session == null)
			throw new RuntimeException("Unexpected: no session!");
		String userName = (String)session.getAttribute(USER_ATTRIBUTE);
		if (userName == null) {
			SecurityContext securityCtx = (SecurityContext)session.getAttribute("SPRING_SECURITY_CONTEXT");
			if (securityCtx != null && securityCtx.getAuthentication() != null)
				if (securityCtx.getAuthentication().isAuthenticated()) 
					userName = ((UserDetails)securityCtx.getAuthentication().getPrincipal()).getUsername();
			session.setAttribute(USER_ATTRIBUTE, userName);
		}
		if (userName == null)
			throw new RuntimeException("Unexpected: no session!");
		
		mav.addObject("user", userName);		
		mav.addObject("cluster", clusterName);
		return mav;
	}
	
    @RequestMapping("/password/set")
	public ModelAndView password(HttpServletRequest request, HttpServletResponse response,  
			@RequestParam(value="user") String user,
			@RequestParam(value="oldPassword") String oldPassword,
			@RequestParam(value="password") String password,
			@RequestParam(value="password2") String password2) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("password");
 
        if (password == null || password2 == null || password.trim().isEmpty() || !password.equals(password2)) {
    		mav.addObject("error", "Invalid or mismatched password");
    		return mav;
        }
        
        String error = setUserPassword(user, oldPassword, password);
        if (error != null) {
    		mav.addObject("error", error);
    		return mav;
        }

		try {
			response.sendRedirect(request.getContextPath() + "/browse");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}
	
	@RequestMapping(value="/browse")
	public ModelAndView browse(@RequestParam(value="entityName", required=false) String entityName,
							   @RequestParam(value="tenantId", required=false) Integer tenantId, 
							   @RequestParam(value="filter", required=false) String filter,
							   @RequestParam(value="orderBy", required=false) Integer orderBy, 
							   @RequestParam(value="order", required=false) Integer order,
							   @RequestParam(value="fields", required=false) String fields) {
        ModelAndView mav = new ModelAndView();
		browse(mav, entityName, tenantId, filter, orderBy, order, fields);
		return mav;
	}
	
	@SuppressWarnings("unchecked")
	private void browse(ModelAndView mav, String entityName, Integer tenantId, String filter, Integer orderBy, Integer order, String fields) {
		mav.setViewName("main");
		mav.addObject("cluster", clusterName);

		LinkedHashMap<Integer, String> tenants = dataStorage.getTenants();
		mav.addObject("tenants", tenants);
			mav.addObject("tenantId", tenantId != null ? tenantId : tenants.keySet().iterator().next());
		
		Set<String> entities = dataStorage.entityNames();
		mav.addObject("entities", entities);
		mav.addObject("entityName", entityName != null ? entityName : entities.iterator().next());
		
		mav.addObject("filter", filter == null ? "" : filter.replace("\"", "&quot;"));
		mav.addObject("orderBy", orderBy);
		mav.addObject("order", order);
		mav.addObject("fields", fields == null ? "" : fields.replace("\"", "&quot;"));
		
		LinkedHashMap<Long, String> result = new LinkedHashMap<Long, String>();
		mav.addObject("result", result);
		
		if (entityName != null)
			try {
				Class entityClass = dataStorage.entityClass(entityName);

				String fieldsEL = fields != null && !fields.trim().isEmpty() ? fields.trim() : "${#root.toString()}";
				Expression expression = new SpelExpressionParser().parseExpression(fieldsEL, new ELTemplateParserContext());
				
				Transaction tx = dataStorage.transaction(tenantId);
				Criteria criteria = parseCriteria(filter);
				
				for (Object row : tx.find(entityClass, criteria, Collections.singletonList(parseOrderBy(orderBy == 2, order == 2)), MAX_ROWS)) {
					result.put(((StoredBean)row).getId(), expression.getValue(new SpringELCtx(row), String.class).replace("\"", "&quot;"));
				}
			} catch (Exception e) {
				mav.addObject("error", e.getMessage());
			}
	}

	@SuppressWarnings("unchecked")
	@RequestMapping("/details/{entityName}/{tenantId}/{id}")
	public ModelAndView details(@PathVariable Integer tenantId, @PathVariable String entityName, @PathVariable Long id) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("details");
		mav.addObject("tenantId", tenantId);
		mav.addObject("entityName", entityName);
		mav.addObject("id", id);
		mav.addObject("message", entityName + " [id = " + id + "]");

		Class entityClass = dataStorage.entityClass(entityName);
        try {
    		Gson gson = new GsonBuilder().setDateFormat(JSON_DATE_FORMAT).setPrettyPrinting().create();
        	Entity result = dataStorage.transaction(tenantId).get(entityClass, id);
    		mav.addObject("result", result == null ? "Not Found" : gson.toJson(result));
		} catch (Exception e) {
			mav.addObject("error", e.getMessage());
		}
        
		return mav;
	}
	
	@SuppressWarnings("unchecked")
	@RequestMapping("/delete/{entityName}/{tenantId}/{id}")
	public ModelAndView delete(@PathVariable Integer tenantId, @PathVariable String entityName, @PathVariable Long id) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("details");
		mav.addObject("result", "");

		Class entityClass = dataStorage.entityClass(entityName);
        try {
        	Transaction tx = dataStorage.transaction(tenantId);
        	tx.delete(entityClass, Criteria.eq("id", id));
        	tx.commit();
			mav.addObject("message", "Deleted " + entityName + " [id = " + id + "].<br/>Please refresh the search results.");
		} catch (Exception e) {
			mav.addObject("error", e.getMessage());
		}
        
		return mav;
	}
	
	@RequestMapping("/update/{entityName}/{tenantId}/{id}")
	public ModelAndView update(@PathVariable Integer tenantId, @PathVariable String entityName, @PathVariable Long id, @RequestParam(value="record") String record) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("details");
		mav.addObject("tenantId", tenantId);
		mav.addObject("entityName", entityName);
		mav.addObject("id", id);
 
        try {
    		Gson gson = new GsonBuilder().setDateFormat(JSON_DATE_FORMAT).setPrettyPrinting().create();
        	Transaction tx = dataStorage.transaction(tenantId);
           	Entity entity = (Entity)gson.fromJson(record, dataStorage.entityClass(entityName));
            tx.update(entity, false);
        	tx.commit();
    		mav.addObject("result", gson.toJson(entity));
			mav.addObject("message", "Updated " + entityName + " [id = " + id + "].<br/>Please refresh the search results.");
		} catch (Exception e) {
    		mav.addObject("result", record);
			mav.addObject("error", e.getMessage());
		}
        
		return mav;
	}
	
	@RequestMapping("/insert/{entityName}/{tenantId}")
	public ModelAndView insert(@PathVariable Integer tenantId, @PathVariable String entityName, @RequestParam(value="record") String record) {
        ModelAndView mav = new ModelAndView();
        mav.setViewName("details");
		mav.addObject("tenantId", tenantId);
		mav.addObject("entityName", entityName);
 
        try {
    		Gson gson = new GsonBuilder().setDateFormat(JSON_DATE_FORMAT).setPrettyPrinting().create();
        	Transaction tx = dataStorage.transaction(tenantId);
        	Entity entity = (Entity)gson.fromJson(record, dataStorage.entityClass(entityName));
        	entity.setId(null);
        	entity.setTenantId(null);
        	tx.insert(entity);
        	tx.commit();
        	Long id = entity.getId();
    		mav.addObject("result", gson.toJson(entity));
    		mav.addObject("id", id);
			mav.addObject("message", "Inserted " + entityName + " [id = " + id + "].<br/>Please refresh the search results.");
		} catch (Exception e) {
    		mav.addObject("result", record);
			mav.addObject("error", e.getMessage());
		}
        
		return mav;
	}
	
	private Criteria parseCriteria(String filter) {
		Criteria criteria = null;
		filter = filter.trim();
		if (!filter.isEmpty()) {
			String criteriaPrefix = "T(com.px100systems.data.core.Criteria).";
			while (filter.indexOf(" (") != -1)
				filter = filter.replace(" (", "(");
			filter = filter.replace("icontainsText(", "icText(");
			for (String token : new String[]{"containsText", "startsWithText", "endsWithText", "between", "and", "or", "not", "eq", "ne", "lt", "le", "gt", "ge", "in"})
				filter = filter.replace(token + "(", criteriaPrefix + token + "(");
			filter = filter.replace("icText(", criteriaPrefix + "icontainsText(");
			criteria = new SpelExpressionParser().parseExpression(filter).getValue(Criteria.class);
		}
		return criteria;
	}
	
	@SuppressWarnings("rawtypes")
	private String parseOrderBy(boolean orderByModifiedAt, boolean descending) {
		return (orderByModifiedAt ? "modifiedAt" : "id") + " " + (descending ? "DESC" : "ASC");
	}
	
	public static final String ERROR_PAGE = "error";
	
    @ExceptionHandler(Throwable.class)
    public ModelAndView handleException(Throwable e) {
    	log.error(e.getMessage(), e);
    	
        ModelAndView mav = new ModelAndView();
        mav.setViewName(ERROR_PAGE);
        mav.addObject("error", e.getMessage());
        return mav;
    }
}
