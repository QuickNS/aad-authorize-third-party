# Azure Active Directory - Authorization of third-party access to API

The code in this repo is meant to describe a scenario where we want to allow an entity to access an Azure Active Directory protected API on behalf of multiple other entities - the aggregator scenario.

The problem and the proposed solution are detailed in this documentation. The repository includes code both in Java and C# (.NET core) implementing the scenario and the proposed solution.

## Introduction

A common pattern for protecting an API with Azure Active Directory is the usage of access tokens that are sent in the Authorization header of an HTTP request. This token will consist on a number of claims that can be validated by the API to determine if the requester is authorized to access the API itself and the resources exposed by it.

In a typical example, we use the OAUTH client credentials grant flow to authorize an application to access an API.

TBD: add diagram

The requesting application requests an access token from Azure AD to access the protected API. The generated token is a JWT (Json Web Token) including the following claims:

TBD: add table

That token is then passed on the Authorization header of any HTTP requests to the protected API:

```bash
Bearer token
```

We can use the `roles` claim in the token to authorize access to a particular API method. Roles are meant to be generic though, so it might not be enough to check this claim as we may be building a multi-tenant system in which we need to protect information from leaking. An additional check might be required to verify the requester ID has access to the resources retrieved by the API call.

### Example: using requester ID as the resource identifier

This API call is meant to list Files that belong to a particular entity in a multi-tenant environemnt. In this REST API, we use the `app_id` as the resource identifier, meaning that we will only ever retrieve files owned by that entity.

```bash
<some url>
```

This is a safe implementation however it lacks flexibility. There are some scenarios where we might want to specify the resources on the request itself.

### Example: authorizing requester ID access to resources

This is a modified version of the previous API where we specify an ID in the request, to filter the files that are retrieved.

```bash
<some url>
```

A typical scenario where this is useful would be a `admin application` that wants to retrieve files from a client entity as part of an automated process.

The problem here is that the token presented has no connection to the resources being accessed. That means that the API needs to do some additional work to validate this request and make sure it's not trying to retrieve unauthorized resources from a different client entity.

< add some diagram >

The `admin` scenario can be easily solved by using a specific role (like `sys_admin`) that would grant access to all resources and is only assigned to the `admin application` - but this won't be an option in other scenarios.

### The problem: the aggregator scenario

In more advanced scenarios, we may want to have client entities that act on behalf of other clients. A typical example is an aggregator entity that interacts with our system on behaful of several other clients: these clients can access their data directly, but they can also delegate some operations to the aggregator.

Currently, there is no clean way of doing this in Azure Active Directory as there aren't any claims in app access tokens that can be used to represent this aggregator-client relationship.

< add some diagram >

The typical solution for this problem is to keep a system-managed ledger of authorizations: some table that connects requester IDs to resources. And we would additionally have to build some process to update that table when clients authorize/deny aggregators to act on their behalf.

This means that we now have two different places to manage authorization: the Azure Active Directory app registrations and our internal ledger which will add complexity and potential security issues to our system.

## Solution

Some requirements we want to meet with the proposed solution:

- All permission management is done in Azure Active Directory
- Permissions are granular - aggregator is given access to specific client permissions
- Identities are kept specific - both aggregators and clients have their own identity
- API can be protected by standard token security checks


