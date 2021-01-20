# Azure Active Directory - Authorization of third-party access to API

The code in this repo is meant to describe a scenario where we want to allow an identity to access an Azure Active Directory protected API on behalf of one or multiple other identities.

The problem and the proposed solution are detailed in this documentation. The repository includes code both in Java and C# (.NET core) implementing the scenario.

## Introduction

A common pattern for protecting an API with Azure Active Directory is the usage of access tokens that are sent in the Authorization header of an HTTP request. This token will consist on a number of claims that can be validated by the API to determine if the requester is authorized to access the API itself and the resources exposed by it.

In a typical example, we use the OAUTH client credentials grant flow to authorize an application to access an API.

![oauth flow](content/oauth.jpg)

Using Azure AD, both the API and the client application need to have a corresponding app registration, and the client app needs to have a set of permissions (roles) over the API.

The requesting application requests an access token from Azure AD to access the protected API. The generated token is a JWT (Json Web Token) including a set of claims that include the following:

- `aud` - specifying the audience for this token, tipically the App URI for the API in Azure AD
- `appid` - the client ID for the client application registration in Azure AD
- `roles` - the set of roles granted to the calling application, as defined in the API permissions section in Azure AD.

> Note: the claim names will vary depending on OAuth version. This is for v1, however, corresponding claims exist on v2 tokens.

In a simple token validation process we can use these claims to check the following rules:

- We can use the `aud` claim to ensure the token is meant for our protected API.

- We can use the `roles` claim in the token to authorize access to a particular API method. Roles are meant to be generic though, so it might not be enough to check this claim as we may be building a multi-tenant system in which we need to protect information from leaking.

- An additional check might be required to verify the `appid` matches an identity that has access to the resources retrieved by the API call.

This is a standard practice in OAUTH flows in which that determination is done at the resource level: for instance, I can grab a token for an email service providing me with `Mailbox.Read` access, but the actual determination that I have access to my mailbox and not someone else's mailbox is done by the email service rather than something that is included in the token itself.

### Example: using requester ID as the resource identifier

This API call is meant to list Files that belong to a particular entity in a multi-tenant environment. In this REST API, we use the `appid` contained in the token as the resource identifier, meaning that we will only ever retrieve files owned by that entity.

```bash
GET https://<base_url>/api/v1/Files
```

This is a safe implementation however it lacks flexibility. In this case we don't need to do any additional checks because the identity included in the token is our data filter. However, there are some scenarios where we might want to specify the resources on the request itself.

### Example: authorizing requester ID access to resources

This is a modified version of the previous API where we specify an ID in the request, to filter the files that are retrieved.

```bash
GET https://<base_url>/api/v1/{customerId}/Files
```

This type of API is useful in any scenario where we want to allow a particular identity to access data from other entities.

The problem here is that the token presented might have no direct connection to the resources being accessed. That means that the API needs to do some additional work to validate this request and make sure the calling application is not trying to retrieve unauthorized resources from a different customer.

### The problem: the aggregator scenario

In more advanced scenarios, we may want to have client identities that act on behalf of other identities. A typical example is an `aggregator` entity that interacts with our system on behalf of several other clients: these clients can access their data directly, but they can also delegate some operations to the aggregator.

Currently, there is no clean way of doing this in Azure Active Directory as there aren't any claims in app access tokens that can be used to represent this aggregator-client relationship.

The typical solution for this problem is to keep a system-managed ledger of authorizations: some table that connects requester IDs to resources. And we would additionally have to build some process to update that table when clients authorize/deny aggregators to act on their behalf.

![token validation](content/tokenValidation.jpg)

This means that we would now have two different places to manage authorization: the Azure Active Directory app registrations and our permissions database which will add complexity and potential security issues to our system.

## Solution

Some requirements we want to meet with the proposed solution:

- All permission management is done in Azure Active Directory
- Permissions are granular - aggregator is given access to specific client permissions
- Identities are kept specific - both aggregators and clients have their own identity, and we want to be able to track if a request is done by a client to access their own data, or by an aggregator acting on behalf of the client
- API can be protected by standard token security checks

To achieve this we propose that the *aggregator* scenario is implemented using a two-token system:

![aggregator oauth](content/aggregatorDiagram.jpg)

In this system, the aggregator requests a token from Azure AD that will grant access to his identity over the protected API.

In order to access customer's data, the request must provide a second token in a custom HTTP Header. This seconday token grants access to the aggregator ID over the customer's data and includes its own set of roles that allow for granular permission setting.

Both tokens are generated by Azure AD by specifying a different scope on the token request: the standard authorization token targets the protected API's ID, the secondary token targets the customer ID.

## A practical example

Let's say we have the following app registrations in Azure AD:

- `secureAPI` - is the app registration for the Web API, it defines roles such as "`Files.List`" and "`Files.Write`"
- `customerA` - is the app registration for one customer. This allows the customer to request tokens with this identity
- `aggregatorX` - is the app registration for an aggregator entity. This allows the aggregator to request tokens with this identity.

Let's three 3 possible cases for accessing the following URL:

```bash
GET https://<base_url>/api/v1/customerA/Files
```

### customerA tries to access their own data

In this case, customerA only needs to request a token for accessing the API and include it in the authorization header:

| claim | value                       |
|-------|-----------------------------|
| aud   | secureAPI scope             |
| appid | customerA                   |
| roles | "Files.Read", "Files.Write" |
</br>

**Validation is successful** because the audience is correct, the requested role for this operation "`Files.Read`" is included in the token and the identity of the requester (appid) matches the resource ID specified in the URL.

### aggregatorX tries to access customerA data without supplying the additional token

To access the API, aggregatorX requests a token and includes it in the authorization header:

| claim | value                       |
|-------|-----------------------------|
| aud   | secureAPI scope             |
| appid | aggregatorX                 |
| roles | "Files.Read", "Files.Write" |
</br>

The requests **will not be authorized** because the appid doesn't match the resource ID specified in the URL and the additional token was not provided. Based on the token provided we can only infer that aggregatorX has enough rights to call the API, but we have no indication that is authorized to access customerA data.

### aggregatorX accesses customerA data supplying two tokens

As depicted in the diagram above, aggregatorX will request two tokens: one to access the API and another one to access customerA's data:

**Token in Authorization Header**:

| claim | value                       |
|-------|-----------------------------|
| aud   | secureAPI scope             |
| appid | aggregatorX                 |
| roles | "Files.Read", "Files.Write" |
</br>

**Token in Custom Header**

| claim | value                       |
|-------|-----------------------------|
| aud   | customerA scope             |
| appid | aggregatorX                 |
| roles | "customerA.Files.Read"      |
</br>

**Validation is successful** because we can infer that aggregatorX has enough rights to access the API and the secondary token provides indication that aggregatorX has access to customerA's data.

Notice how we can apply a different set of permissions at the customer level, in this case aggregatorX only has `Read` access to customerA's data.

> The roles are prefixed with the customer name to make it easier to identify in this example, but in a more streamlined implementation they can just mimic the roles defined at the API level as it will simplify the validation code.

## Token validation process

The following diagram details the entire validation process using the two token approach:

![flowchart](content/flowchart.jpg)

## Configuring Azure AD

To support the two-token system, we need to have the appropriate configurations in place on Azure AD.

### Creating the API registration

1. Sign in to the [Azure portal](https://portal.azure.com) and make sure you are in the correct Azure AD tenant.
2. In the portal menu, select the **Azure Active Directory** service, and then select **App registrations**.
3. Select **New registration**.
   - In the **Name** section, enter a meaningful application name, for example `my-protected-api`.
   - In the **Supported account types** section, select **Accounts in this organizational directory only ({tenant name})**.
   - Click **Register** button at the bottom to create the application.

### Exposing the API

Next step is creating an Application ID URI to identity your API for calling applications.

1. In the application registration for your application, select "Expose an API"
2. Next to the Application ID URI, click Set
3. By default, the application registration portal recommends that you use the resource URI `api://{app_client_id}`. This URI is unique but not human readable. If you change the URI, make sure the new value is unique. In this example we are using `api://my-protected-api`
4. Click Save

> Note: record the value of the Application ID URI (step 3) as you will need to supply this to client apps later.

### Creating App Roles

Because the clients of this API will be applications registered in Azure AD, we don't configure scopes and delegated permissions, rather we declare and expose only application permissions.

Currently in the portal there is no GUI to expose application permissions, so it needs to be done by manually editing the application **Manifest**:

1. In the application registration for your application, select Manifest.
2. To edit the manifest, find the appRoles setting and add application roles. The role definitions are provided in the following sample JSON block.
3. Leave allowedMemberTypes set to "Application" only.
4. Make sure id is a unique GUID.
5. Make sure displayName and value don't contain spaces.
6. Save the manifest.

The following sample shows the contents of `appRoles`, where the value of `id` can be any unique GUID:

```json
"appRoles": [
    {
    "allowedMemberTypes": [ "Application" ],
    "description": "Access the API with Read privileges",
    "displayName": "API.Read",
    "id": "7fab3cc5-9f2f-4094-8fdd-1a366c88255a",
    "isEnabled": true,
    "lang": null,
    "origin": "Application",
    "value": "API.Read"
    },
    {
    "allowedMemberTypes": [ "Application" ],
    "description": "Access the API with Write privileges",
    "displayName": "API.Write",
    "id": "5a5f9622-3667-4a00-9ecb-113ea3b788b7",
    "isEnabled": true,
    "lang": null,
    "origin": "Application",
    "value": "API.Write"
    }
    ],
```

> Note: this example creates two roles which can be used to determine two permission levels for APIs. These roles will be present in the JWT as a claim and can be validated by the API to authorize the request.

These roles are universal and not client specific. As an example: if an app with a particular clientId is granted "API.Read" it implies that it has permission to invoke the API with that access level, but further authorization is required to determine if this token can perform operations on resources pertaining to a specific client ID.

## Registering Client Applications in Azure AD

In this section we register a client application in Azure AD to represent an API client. The same process can be used to enable access to other calling applications (like test and development tools) that require access to the API in an authorized flow. We also assign it one or more of the app roles created in the previous section.

### Creating the Client app registration

1. Sign in to the [Azure portal](https://portal.azure.com) and make sure you are in the correct Azure AD tenant.
2. In the portal menu, select the **Azure Active Directory** service, and then select **App registrations**.
3. Select **New registration**.
   - In the **Name** section, enter a meaningful application name. In this example we will use `clientXYZ`
   - In the **Supported account types** section, select **Accounts in this organizational directory only ({tenant name})**.
   - Click **Register** button at the bottom to create the application.

### Assigning permissions

1. In the Application menu blade, click on the **Certificates & secrets**, in the **Client secrets** section, choose **New client secret**:
   - Type a key description (for instance `app secret`),
   - Select a key duration of either **In 1 year**, **In 2 years**, or **Never Expires** as per your security concerns.
   - The generated key value will be displayed when you click the **Add** button. Copy the generated value for use in the steps later.
   - You'll need to provide this key later to include in the client application's configuration files. This key value will not be displayed again, and is not retrievable by any other means, so make sure to note it from the Azure portal before navigating to any other screen or blade.

2. In the Application menu blade, click on the **API permissions** in the left to open the page where we add access to the Apis that your application needs.
   - Click the **Add a permission** button and then,
   - Ensure that the **My APIs** tab is selected
   - Select the **my-protected-api** that was created earlier
   - In the **Application permissions** section, select the applicable permissions (in this example it should be `API.Read` and `API.Write`)
   - Select the **Add permissions** button at the bottom.

3. At this stage, the permissions are assigned correctly but since the client app does not allow users to interact, the user's themselves cannot consent to these permissions.
   To get around this problem, we'd let the [tenant administrator consent on behalf of all users in the tenant](https://docs.microsoft.com/azure/active-directory/develop/v2-admin-consent).
   Click the **Grant admin consent for {tenant}** button, and then select **Yes** when you are asked if you want to grant consent for the requested permissions for all account in the tenant.
   **You need to be the tenant admin to be able to carry out this operation.**

This screenshot shows the state of a client application with permissions to access the **my-protected-api** application with the **API.Read** and **API.Write** roles:

![Client App Permissions](./content/client_app_permissions.png)

### Enabling third-party access

Additionally, we now need to create a `scope` for this client identity so that we can allow another identity to act on its behalf.

1. In the application registration for your application, select "Expose an API"
2. Next to the Application ID URI, click Set
3. By default, the application registration portal recommends that you use the resource URI `api://{app_client_id}`. This URI is unique but not human readable. If you change the URI, make sure the new value is unique. In this example we are using `api://clientXYZ`
4. Click Save

We also need to define new roles that are client specific. The idea is that, when we give permissions to another identity to access this identity resources, we want to have granular control over the permissions that are added. As such, we will create replicas of the API roles defined before:

Click the **Manifest** button on the `clientXYZ` app registration and replace the `appRoles` property with the following values:

```json
    "appRoles": [
    {
    "allowedMemberTypes": [ "Application" ],
    "description": "Access clientXYZ data with Read privileges",
    "displayName": "clientXYZ.Read",
    "id": "7fab3cc5-9f2f-4094-8fdd-1a366c88255b",
    "isEnabled": true,
    "lang": null,
    "origin": "Application",
    "value": "clientXYZ.Read"
    },
    {
    "allowedMemberTypes": [ "Application" ],
    "description": "Access clientXYZ data with Write privileges",
    "displayName": "clientXYZ.Write",
    "id": "5a5f9622-3667-4a00-9ecb-113ea3b788b8",
    "isEnabled": true,
    "lang": null,
    "origin": "Application",
    "value": "clientXYZ.Write"
    }
    ],
```

Don't forget to **Save** the manifest.

## Creating the aggregator app registration

Now, create another client application to represent the `aggregator`. In this example, we will name it `aggregatorABC`.

The process is similar to registering the client application, but this time, when assigning permissions, make sure you assign permissions both to the `my-protected-api` (both roles) but also to the `clientXYZ` app roles (only Read).

Don't foget to also create a secret and take a note of the value as that will be required to request tokens from Azure AD.

This screenshot shows the state of the new client application with permissions to access the **my-protected-api** and also authorized to access **customerXYZ**'s resources in **read only** mode.

![Client App Permissions](./content/aggregator_permissions.png)

## Gathering all configuration values

This is a list of values you need to have noted down:

### `my-protected-api`

On the app registration **Overview** page:

- The **Application (client) ID**
- The **Directory (tenant) ID**
- The **Application ID URI**

### `clientXYZ`

On the app registration **Overview** page:

- The **Application (client) ID**
- The **Application ID URI**

Additionally you need the **Secret** value generated earlier, which can not be retrieved at this point.

### `aggregatorABC`

On the app registration **Overview** page:

- The **Application (client) ID**

Additionally you need the **Secret** value generated earlier, which can not be retrieved at this point.

## Configuring the Sample

With the above values collected we can now configure the sample to run and walkthrough the scenario. There is a Java and .NET version of the API on this app which will act as our `my-protected-api` app.

Open either the Java or .NET folder and modify the values in their respective configuration files. You only need the `my-protected-api` values listed above.

The *client* is just a series of HTTP requests defined in the client_requests.http file in this repo. To execute the http requests, we will need the following steps:

1. Install `humao.rest-client` extension in VSCode
2. Open the root of the repository in VSCode
3. Add these settings to your `.vscode/settings.json`. You may need to create the file if it doesn't already exist:

    ```json
    {
        "rest-client.environmentVariables": {
            "$shared": {
                "clientId": "REPLACE_WITH_CLIENT_ID_OF_CLIENTXYZ_APP",
                "clientSecret": "REPLACE_WITH_CLIENTXYZ_SECRET",
                "clientScope" : "REPLACE_WITH_CLIENTXYZ_APPLICATION_ID_URI",
                "aggregatorId": "REPLACE_WITH_CLIENT_ID_OF_AGGREAGTORABC_APP",
                "aggregatorSecret": "REPLACE_WITH_AGGREGATORABC_SECRET",
                "tenantId": "REPLACE_WITH_AZURE_AD_TENANT_ID",
                "apiScope": "api://<API_ID_URI/.default",
                "apiUrl": "http://localhost:8080"
            }
        }
    }
    ```

> Note: In this OAUTH flow, applications cannot ask for specific scopes, and must use the `resource/.default` format where `resource` is the Application ID URI created for the `my-protected-api` application.

## Running the Sample

Start the Java/.NET API application so it's running locally, or deploy it to a running web server.

> Update the `apiUrl` property on the `.vscode/settings.json` file so that the requests are properly routed.

Then simply follow the instructions on the `client_requests.http` file to walkthrough the scenario. The file includes comments to explain what you should be seeing at each step.

If everything is correctly configured we should have the following setup working:

- We can requests tokens from Azure AD by using the `clientXYZ` or `aggregatorABC` identities.
- Issued tokens include the `API.Read` and `API.Write` roles and are targeted at the `my-protected-api` audience
- The Java/.NET application is able to validate these tokens and verify the roles within
- The `aggregatorABC` is able to request a token for the `clientXYZ` scope and send that token in a custom header
- The Java/.NET application is able to validate the secondary token to determine that `aggregatorABC` has read-only access to `clientXYZ` resources


