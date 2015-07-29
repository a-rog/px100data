<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE HTML>
<html>
	<head>
		<meta http-equiv="content-type" content="text/html; charset=UTF-8">
		<link type="text/css" rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/db-browser.css">
		<title>DB Browser: ${cluster}</title>
	</head>
    <body>
		<div class="pageHeader">
			<span>DB Browser: ${cluster}</span>
			<span style="float:right;">
				<a class="pageHeaderLinks" href="${pageContext.request.contextPath}/password/change">Change Password</a>
				&nbsp;&nbsp;
				<a class="pageHeaderLinks" href="${pageContext.request.contextPath}/j_spring_security_logout">Logout</a>
			</span>
		</div>
		<table style="width: 100%">
			<tr>
				<td colspan="2" style="background-color: #D8D8D8; border-style: solid; border-width: 1px; padding: 5px;">
					<form action="${pageContext.request.contextPath}/browse" method="post">
						<b>Entity:</b> 
						<select name="entityName">
					    	<c:forEach var="entity" items="${entities}">
					    		<option value='${entity}' ${entity.equals(entityName) ? "selected" : ""}>${entity}</option>	
							</c:forEach>
						</select>				
						&nbsp;&nbsp;&nbsp;&nbsp;
						<select name="tenantId">
					    	<c:forEach var="tenant" items="${tenants.entrySet()}">
					    		<option value='${tenant.key}' ${tenant.key.toString().equals(tenantId.toString()) ? "selected" : ""}>${tenant.value}</option>	
							</c:forEach>
						</select>				
						&nbsp;&nbsp;&nbsp;&nbsp;
						<b>Filter:</b> 
						<input type="text" size="70" name="filter" value="${filter}"/>		
						&nbsp;&nbsp;&nbsp;&nbsp;
						<b>Order by:</b> 
						<select name="orderBy">
					    	<option value='1' ${orderBy == 1 ? "selected" : ""}>ID</option>	
					    	<option value='2' ${orderBy == 2 ? "selected" : ""}>Modified</option>	
						</select>
						&nbsp;
						<select name="order">
					    	<option value='1' ${order == 1 ? "selected" : ""}>ASC</option>	
					    	<option value='2' ${order == 2 ? "selected" : ""}>DESC</option>	
						</select>
						<br/>
						<b>Output:</b> 
						<input type="text" size="160" name="fields" value="${fields}"/>		
						&nbsp;&nbsp;&nbsp;&nbsp;
						<input type="submit" class="buttonText" value="Search"/>			
					</form>
				</td>
			</tr>
			<tr>
				<td style="width: 60%; vertical-align: top; border-right-style: solid; border-right-width:1px;">
			    	<c:forEach var="item" items="${result.entrySet()}">
			    		<a href="${pageContext.request.contextPath}/details/${entityName}/${tenantId}/${item.key}" target="detailsFrame">${item.key}</a> ${item.value}<br/>	
					</c:forEach>
			    	<div>
						<c:if test="${error != null}">
							<span class="errorText">${error}</span>
						</c:if>
			    	</div>
				</td>
				<td style="width: 40%; vertical-align: top;">
					<iframe name="detailsFrame" id="detailsFrame" width="100%" height="550px" style="border-style:none;"></iframe>
				</td>
			</tr>
		</table>
	</body>
</html>
