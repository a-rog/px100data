<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE HTML>
<html>
	<head>
		<meta http-equiv="content-type" content="text/html; charset=UTF-8">
		<link type="text/css" rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/db-browser.css">
	</head>
	<body>
    	<div>
			<c:if test="${error != null}">
				<span class="errorText">${error}</span>
			</c:if>
			<span class="messageText">${message}</span>
    	</div>
		<c:if test="${entityName != null}">
			<form id="updateForm" method="post">
				<textarea name="record" rows="35" cols="70">${result}</textarea>
			</form>
		</c:if>
		<c:if test="${id != null}">
			<form id="deleteForm" action="${pageContext.request.contextPath}/delete/${entityName}/${tenantId}/${id}" method="post">
			</form>
			<input type=button class="buttonText" value="Update" onClick="if (confirm('Update the record?')) {
				document.getElementById('updateForm').action = '${pageContext.request.contextPath}/update/${entityName}/${tenantId}/${id}';
				document.getElementById('updateForm').submit();
			}">
			&nbsp;&nbsp;			
		</c:if>
		<c:if test="${entityName != null}">
			<input type=button class="buttonText" value="Insert" onClick="if (confirm('Insert the record?')) {
				document.getElementById('updateForm').action = '${pageContext.request.contextPath}/insert/${entityName}/${tenantId}';
				document.getElementById('updateForm').submit();
			}">
			&nbsp;&nbsp;
		</c:if>			
		<c:if test="${id != null}">
			<input type=button class="buttonText" value="Delete" onClick="if (confirm('Delete the record?')) document.getElementById('deleteForm').submit();">			
		</c:if>
	</body>
</html>