# Configuring the .NET Core sample

Inside this folder, find the `appsettings.json.template`.

Make a copy of it and rename it to `appsettings.json`.

Open the new file in a code editor and replace the values in there using the following values from the `my-protected-api` app registration:

### `my-protected-api`

- The **Application (client) ID** - <API_CLIENT_ID>
- The **Application ID URI** - <APPLICATION_ID_URI>
- The **Directory (tenant) ID** - <TENANT_ID>

> Note: if you made a custom Application Id URI like `api://my-protected-api`, use `my-protected-api` as the API_CLIENT_ID. If not, use the Application ID Guid as shown in the portal.