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
			<span>Password for DB Browser: ${cluster}</span>
		</div>
		<div style="position:absolute; left:50%; margin-left:-250px; width:600px; top:50%; margin-top:-150px; height:300px">
			<form id="activationForm" method="POST" action="${pageContext.request.contextPath}/activate">
				<input type="hidden" name="user" value="${user}"/>      		
				<table>
					<tr>
						<td colspan="2" class="largeText" style="text-align:center;"><b>Set Password for ${user}</b></td>
					</tr>
					<tr>
						<td><b>Password:</b></td>
						<td><input type='password' name='password'/></td>
					</tr>
					<tr>
						<td><b>Retype:</b></td>
						<td><input type='password' name='password2'/></td>
					</tr>
					<tr>
						<td/>	
						<td>
							<input type="submit" class="buttonText" value="Submit"/>
						</td>
					</tr>
					<tr>
						<td/>
						<td>
							<c:if test="${error != null}">
								<span class="errorText">${error}</span>
							</c:if>
						</td>
					</tr>
				</table>
			</form>
		</div>
	</body>
</html>
