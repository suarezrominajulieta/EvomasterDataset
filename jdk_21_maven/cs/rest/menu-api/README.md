# menu-api

A restaurant menu REST API, loosely ported from the .NET `Menu.API` of the EvoMaster
benchmark ([chayxana/Restaurant-App](https://github.com/chayxana/Restaurant-App)).

The project is a case study for research on REST API fuzzing. Two things are being
measured, and everything else in here exists to support them:

- **`multipart/form-data`.** Seven endpoints take multipart bodies between them, covering
  primitive parts, file parts, array-of-file parts and an object part sent as its own
  `application/json` section, plus per-part content types declared through the schema's
  `encoding` block. Behind them sits one validation table where **every way of getting an
  upload wrong has a status code of its own** — `400`, `403`, `409`, `413`, `415` and
  `422` all mean different things here. A client that builds the body correctly and a
  client that does not can be told apart from the outside, without reading any logs.
- **JSON Patch (RFC 6902).** Three entities accept `application/json-patch+json`, and a
  patch can fail in ten distinguishable ways.

The database starts **seeded**, from the same CSV files the .NET original shipped, so a
fuzzer arriving at a freshly started instance has rows to read, delete and patch straight
away.

## Build and run

Java 21 and Maven are required.

```bash
mvn clean package
java -jar target/menu-api-sut.jar
```

The app listens on port 8080; `--server.port=0` picks an ephemeral one. Everything is
stored in an in-memory H2 database created at startup and dropped at shutdown, and the H2
console is disabled. **Uploads are held in that database too, not on disk**, so they die with
the process like everything else and nothing is ever left to clean up — see
[Where the uploads go](#where-the-uploads-go-and-why-the-store-is-bounded).

`openapi.json` in the repository root is the generated schema, so a black-box run needs
nothing started. Regenerate it after changing any endpoint:

```bash
curl http://localhost:8080/v3/api-docs > openapi.json
```

Swagger UI is at `/swagger-ui.html`, and `/` redirects there.

## Endpoints

| Method | Path | Responses |
|---|---|---|
| `GET` | `/api/v1/Categories` | 200 |
| `POST` | `/api/v1/Categories` | 201 + `Location`, 400 |
| `GET` | `/api/v1/Categories/{id}` | 200, 404 |
| `PUT` | `/api/v1/Categories/{id}` | 200, 400, 404 |
| `PATCH` | `/api/v1/Categories/{id}` | 200, 400, 403, 404, 409, 422 |
| `DELETE` | `/api/v1/Categories/{id}` | 204, 404 |
| `POST` | `/api/v1/Categories/{id}/icon` | 200, 400, 403, 404, 413, 415, 422 |
| `GET` | `/api/v1/Currency` | 200 |
| `GET` | `/api/v1/Currency/{code}` | 200, 404 |
| `GET` | `/api/v1/Foods?count=&skip=` | 200, 400 |
| `POST` | `/api/v1/Foods` | 201 + `Location`, 400 |
| `GET` | `/api/v1/Foods/{id}` | 200, 404 |
| `PUT` | `/api/v1/Foods/{id}` | 200, 304, 400, 404, 409 |
| `PATCH` | `/api/v1/Foods/{id}` | 200, 400, 403, 404, 409, 422 |
| `DELETE` | `/api/v1/Foods/{id}` | 204, 404 |
| `POST` | `/api/v1/Foods/UploadFoodImage` | 200, 400, 403, 404, 409, 413, 415, 422 |
| `POST` | `/api/v1/Foods/{id}/picture` | 201, 400, 403, 404, 413, 415, 422 |
| `POST` | `/api/v1/Foods/{id}/pictures/batch` | 200, 400, 403, 404, 409, 413, 415, 422 |
| `POST` | `/api/v1/Foods/import` | 200, 400, 403, 409, 413, 415, 422 |
| `POST` | `/api/v1/Foods/{id}/allergens` | 200, 400, 403, 404, 413, 415, 422 |
| `GET` | `/api/v1/FoodPictures/{id}` | 200, 404 |
| `PATCH` | `/api/v1/FoodPictures/{id}` | 200, 400, 403, 404, 409, 422 |
| `DELETE` | `/api/v1/FoodPictures/{id}` | 204, 404 |
| `PUT` | `/api/v1/FoodPictures/{id}/content` | 200, 400, 403, 404, 413, 415, 422 |
| `GET` | `/Info` | 200 (`text/plain`) |

Every path also answers `405` to a method it does not have and `415` to a body in a media
type it does not take. A `500` is never a documented answer: anything that produces one is
a fault, which is the point of pointing a fuzzer at this.

That is the whole of `openapi.json`. One further route is not in it: `GET /` is a redirect
to the Swagger UI, not an operation.

## What was ported, and what is new

Twenty-six routes in total: **fourteen carried over** from the .NET original and **twelve
added**. Nothing the original had was dropped.

### The fourteen that were already there

| # | Method | Path (here) | Status |
|---|---|---|---|
| 1 | `GET` | `/api/v1/Categories` | unchanged |
| 2 | `GET` | `/api/v1/Categories/{id}` | unchanged (200/**404**, was 204) |
| 3 | `POST` | `/api/v1/Categories` | unchanged (**201** + `Location`, was 200 with no body) |
| 4 | `PUT` | `/api/v1/Categories/{id}` | unchanged |
| 5 | `DELETE` | `/api/v1/Categories/{id}` | unchanged (**204**, was 200) |
| 6 | `GET` | `/api/v1/Foods?count=&skip=` | **signature changed** — was `/Foods/{count?}/{skip?}` |
| 7 | `GET` | `/api/v1/Foods/{id}` | unchanged — but **unreachable** before, colliding with #6 |
| 8 | `POST` | `/api/v1/Foods` | unchanged (201 + `Location`) |
| 9 | `POST` | `/api/v1/Foods/UploadFoodImage` | **carried over as it stood** — same path, same parts (`files[]` + `foodId`) |
| 10 | `PUT` | `/api/v1/Foods/{id}` | unchanged, including the 304 and `deletedPictures` |
| 11 | `DELETE` | `/api/v1/Foods/{id}` | unchanged (204, was 500 through `Entry(null)`) |
| 12 | `GET` | `/api/v1/Currency` | unchanged, the same 162 currencies |
| 13 | `GET` | `/Info` | unchanged, `Menu API` as plain text |
| 14 | `GET` | `/` | unchanged — a redirect; only its target moved, to `/swagger-ui.html` |

Only two changed shape: **#6**, where paging moved into query parameters, and **#14**,
whose target moved because springdoc serves the UI here. Everything else that differs is a
status code, and the reasons for those are in
[the divergence table](#where-this-diverges-from-the-net-original). #14 is the one route
missing from `openapi.json`: it is a redirect, not an operation.

### The twelve that are new

**JSON Patch (3).** None of this existed:

| Method | Path |
|---|---|
| `PATCH` | `/api/v1/Categories/{id}` |
| `PATCH` | `/api/v1/Foods/{id}` |
| `PATCH` | `/api/v1/FoodPictures/{id}` |

**Multipart (6).** The original had `UploadFoodImage` and nothing else:

| Method | Path | What it exercises |
|---|---|---|
| `POST` | `/api/v1/Foods/{id}/picture` | one file part next to optional primitive parts |
| `POST` | `/api/v1/Foods/{id}/pictures/batch` | file parts next to an object part, via `encoding.metadata` |
| `POST` | `/api/v1/Categories/{id}/icon` | a part content type pinned to `image/png` |
| `POST` | `/api/v1/Foods/import` | a `text/csv` part next to a boolean part |
| `PUT` | `/api/v1/FoodPictures/{id}/content` | a multipart body on a **PUT**, not a POST |
| `POST` | `/api/v1/Foods/{id}/allergens` | a PDF part pinned to `application/pdf`, with an ASCII signature |

**FoodPictures (2).**

| Method | Path |
|---|---|
| `GET` | `/api/v1/FoodPictures/{id}` |
| `DELETE` | `/api/v1/FoodPictures/{id}` |

The original had the model, the table, the repository and the service for a picture, but
**no controller**. `FoodPictureService` was reached from exactly two lines, both inside
`FoodsController`: the upload, and the `deletedPictures` list of a `PUT`. So a picture had
an id of its own and no URL of its own. Nothing could be patched, because there was no
address to aim a `PATCH` at, and deleting one meant resending the whole dish with every one
of its fields valid. Both of the old ways still work; these two are a shorter road to the
same place.

**Currency (1).**

| Method | Path |
|---|---|
| `GET` | `/api/v1/Currency/{code}` |

The most interesting of the twelve, because the logic was already written.
`CurrencyProvider.GetByCode()` was there and was already case insensitive, and no
controller ever called it. This gives it a URL, and nothing more: a 200/404 pair out of
code that was already in the repository.

## The multipart endpoints

| # | Endpoint | What it exercises |
|---|---|---|
| 1 | `POST /Foods/UploadFoodImage` | An array of file parts next to a text part. The shape the .NET original had. |
| 2 | `POST /Foods/{id}/picture` | One file part next to two **optional** primitive parts. |
| 3 | `POST /Foods/{id}/pictures/batch` | File parts next to an **object part** sent as `application/json`, via `encoding.metadata.contentType`. |
| 4 | `POST /Categories/{id}/icon` | A file part whose content type is pinned to `image/png` by `encoding.icon.contentType`. |
| 5 | `POST /Foods/import` | A `text/csv` file part next to a **boolean** part, in front of a real parser. |
| 6 | `PUT /FoodPictures/{id}/content` | A multipart body on a **PUT**, to catch a client wired only to POST. |
| 7 | `POST /Foods/{id}/allergens` | A PDF part pinned to `application/pdf`. The twin of 4 — see below. |

Endpoint 3 refuses a request whose caption count does not match its file count, and that
branch is only reachable when the JSON part really did arrive as JSON. Endpoint 5 parses
the CSV for real and, with `dryRun=true`, walks every branch of the parser while storing
nothing.

### Endpoints 4 and 7: telling text from bytes

A client that renders each part to a `String` and sends the body as text cannot deliver every
byte. Java strings hold characters, and Jersey encodes a `String` entity as UTF-8 whenever the
`Content-Type` carries no `charset` — which a multipart `Content-Type` never does. Any byte
from `0x80` up turns into two on the wire. For the signatures checked here that splits cleanly:

| Format | Signature | Sent as UTF-8 text | Survives |
|---|---|---|---|
| PNG | `89 50 4E 47` | `C2 89 50 4E 47` | no |
| JPEG | `FF D8 FF` | `C3 BF C3 98 C3 BF` | no |
| GIF | `47 49 46 38` (`GIF8`) | unchanged | yes |
| WEBP | `52 49 46 46` (`RIFF`) | unchanged | yes |
| PDF | `25 50 44 46 2D` (`%PDF-`) | unchanged | yes |

So endpoint 4 — a category icon pinned to `image/png` — **can never succeed** for such a
client, however clever its content generation. Endpoint 7 is the same shape of request with
the one difference that matters: a pinned content type, a signature check, and a signature that
is plain ASCII. Its trailer check, `%%EOF` near the end, is ASCII too. Measured here, over
HTTP:

| | 4: icon (PNG) | 7: allergens (PDF) |
|---|---|---|
| Real bytes | 200 | 200 |
| Rendered as a UTF-8 `String` | **422** | **200** |

Read together, the two answer a question neither answers alone:

| 4 | 7 | What the client is doing |
|---|---|---|
| never 200 | never 200 | Not producing valid signatures at all |
| never 200 | 200 | Producing them, but sending binary parts as text |
| 200 | 200 | Sending real bytes |

The GIF and WEBP paths on the picture endpoints make the same point, but mixed into one list
of accepted types on one endpoint. Here it is isolated to a single variable. The CSV import
cannot do this job: a CSV has no signature to check.

`AllergenSheetTest.textEncodedBodiesTellThemApart` pins the property down, so it cannot
quietly stop being true.

### The validation table

Shared by all six, in `FileValidationService`:

| Check | Answer |
|---|---|
| A required part is absent | 400 |
| The part carries no `filename`, so it arrived as a form field | 400, with a message saying exactly that |
| Extension outside `.jpg .jpeg .png .gif .webp` (this is what catches `shell.png.jsp`) | 415 |
| Double extension, as in `shell.jsp.png` | 415 |
| A path in the `filename`: `../`, `..\`, or absolute | 403 |
| A control character or null byte in the `filename` | 400 |
| A `filename` longer than 255 characters | 400 |
| A part content type outside the image list | 415 |
| Magic bytes that do not match the declared extension | 422 |
| An empty file | 400 |
| A file larger than 5 MB | 413 |
| More than 10 files in one request | 413 |
| The same `filename` twice in one request | 409 |

The container's own limits are set deliberately high (`10MB` per file, `50MB` per
request) so that a refusal is a visible branch in this code rather than one opaque
container error covering everything.

Nothing a client sends is ever used to build a path. A traversing `filename` is refused
**and** the stored name is safe by construction: files land under
`{timestamp}_{uuid}_{sanitised original}`, and the name the client chose survives only as
a column. This is a test bench, not a real target.

### Where the uploads go, and why the store is bounded

**An accepted upload becomes a row in the in-memory database, not a file on disk.** It
therefore lives exactly as long as the process: a restart is a clean slate, and there is
never anything left over to sweep up. A refused upload stores nothing at all — validation
runs before a byte is kept, so the `422` an upload gets for lying about its magic bytes
costs nothing.

It used to write files, and that was the wrong shape for this application. Everything else
here is dropped when the process ends, because the schema is created and dropped with it.
Uploads were the one exception: they outlived the run that made them, and since every path
that deletes one starts from a row the restart had already dropped, nothing was ever going
to remove them again. Each campaign left its files behind for the next one.

That matters more the better a client gets. The magic byte rule is the last gate, and the
only way through it is to emit real PNG, JPEG, GIF, WEBP or PDF headers — exactly the
behaviour this bench exists to reward. **The better a client gets at multipart, the more of
its uploads are accepted, and the faster they pile up.** Measured while they still went to
disk: uploads near the 5 MB limit landed at roughly 80 MB/s, so a few thousand accepted
uploads was tens of gigabytes that no restart would reclaim.

Holding them as rows moves that cost from the disk to the heap, where it is bounded by
configuration rather than by how much room the machine happens to have:

| Setting | Default | What it does |
|---|---|---|
| `menu.pictures.max-files` | 200 | Past this, the oldest upload goes |
| `menu.pictures.max-bytes` | 32 MB | Past this, the same |
| `menu.pictures.max-per-food` | 25 | Past this, a dish loses its oldest picture — row and bytes together |
| `spring.servlet.multipart.file-size-threshold` | 1 MB | Spring Boot defaults this to `0`, which spools *every* file part to the container temp directory before the application sees it, including parts about to be refused. With the application writing nothing itself, this is the only thing that still could. |

There is no storage path to configure, and nothing is adopted at startup, because there is
nothing there to adopt.

Bounding the bytes is only half of it. A dish free to collect picture rows forever makes
every later `GET /Foods` carry every picture ever sent to it, at about 127 bytes each —
bytes that buy no coverage and are paid on one of the endpoints a fuzzer calls most. With
the per-dish limit the response stops growing:

| Uploads to one dish | Pictures kept | `GET /Foods` |
|---|---|---|
| 0 (just the seed) | 1 | 10.7 KB |
| 50 | 25 | 14.2 KB |
| 300 | 25 | 14.3 KB |

`pictures[]` comes back oldest first, which is also what decides who goes. The ordering key
is a counter rather than a timestamp: a clock resolves to the millisecond on some platforms,
two uploads in the same millisecond tie, and the tie then falls to a random id. It is an
internal column — no DTO carries it, so `openapi.json` does not mention it and no patch can
reach it.

The store **evicts rather than refuses**. A refusal would depend on how much the current
run had already uploaded, so the same request would get different answers in two runs of
the same fuzzer — the one thing a measurement setup cannot afford. Eviction leaves every
status code in the tables above exactly where it was. A picture row whose bytes have been
evicted still reads back normally, because nothing here ever serves those bytes back: it is
the same state the twenty seeded pictures are in from the very start.

## JSON Patch

`Category`, `Food` and `FoodPicture` take `application/json-patch+json`:

```http
PATCH /api/v1/Foods/{id}
Content-Type: application/json-patch+json

[
  { "op": "replace", "path": "/price", "value": 12.5 },
  { "op": "test",    "path": "/currency", "value": "USD" },
  { "op": "remove",  "path": "/description" }
]
```

The answer is `200` with the whole patched entity. The schema declares `op` as an **enum**
rather than a free string, and gives real field names as `path` examples; without that a
fuzzer spends its budget on operations that are rejected before reaching anything
interesting.

| Situation | Answer |
|---|---|
| The entity does not exist | 404 |
| The body is not an array, or is an empty one | 400 |
| An `op` nobody has heard of | 400 |
| A malformed `path`: no leading `/`, or an invalid `~` escape | 400 |
| `move` or `copy` without a `from`, or `add`/`replace`/`test` without a `value` | 400 |
| A type that no longer fits once the patch is applied | 400 |
| Reaching for `/id`, or for a derived field such as `/pictures` or `/category` | 403 |
| `replace` or `remove` naming a field the document does not have | 409 |
| A `test` that fails | 409 |
| A `categoryId` that names nothing | 409 |
| A negative price, a blank name, or an unknown currency code | 422 |

The same mistake can land on a different code depending on how it got there, and that is
deliberate: an unknown `categoryId` is a `400` on `POST /Foods`, where it is part of a
malformed request, and a `409` through `PATCH`, where the request was fine and only
disagrees with what is stored.

## Where this diverges from the .NET original

Fidelity is a guide here, not a contract; the two versions are never compared directly.
The shape of the API is kept, and the places where the original did something expensive to
port or simply pathological are these. Which routes came from the original and which are
new is a separate question, answered in
[What was ported, and what is new](#what-was-ported-and-what-is-new).

| Original .NET | Here | Why |
|---|---|---|
| `Commit()` returns true only when `SaveChanges() == 1`, so a `POST` with nested children saves and then answers 400 | Saved means 2xx | Reproducing it under JPA means counting rows through Hibernate statistics, and it only ever mattered for comparing against the original |
| An absent id gives 204 on Categories and 500 on Foods (`Entry(null)`) | 404 everywhere | The 500 was a disguised null dereference; 404 is just as much a branch and a good deal cleaner |
| Paging in the path, `/Foods/{count?}/{skip?}`, colliding with `/Foods/{id}` and answering 500 on an ambiguous route | `?count=&skip=` | The collision made `GET /Foods/{id}` unreachable, and that endpoint is where PATCH and the uploads start |
| A negative `count` or `skip` reaches the database and comes back as a 500 | 400 with an `ErrorDto` | An explicit branch instead of a driver error |
| `POST` answers 200 with no body | 201 with a `Location` | It hands back the id of what was just created, which every PATCH and upload then needs |
| Unhandled exceptions escape as bare 500s | `ApiExceptionHandler` with an `ErrorDto` | Expected failures get their 4xx; **unexpected ones still escape as 500s**, deliberately, because those are the faults worth reporting |
| `FoodPictureDto` maps back by storing the whole `filePath` as the file name | The `/pictures/` prefix is stripped on the way in | The original round trip kept prefixing itself |
| `Category.ShortName` is `[NotMapped]` and in no DTO | Dropped | It was never reachable |
| No icon on a category | `iconPath`, set by endpoint 4 | Without it the icon upload has no observable effect |
| No allergen sheet on a dish | `allergenSheetPath`, set by endpoint 7 | The same reason |
| Uploads are written under `wwwroot/pictures/` | Held as rows in the in-memory database | They are then dropped with the process, like every other piece of state here. On disk they outlived the run that made them and nothing would ever delete them, because every delete path starts from a row the restart had already dropped |
| `FoodPictureDto` is `{id, filePath}` only | `GET`/`PATCH /FoodPictures/{id}` answer with the fuller record | `{id, filePath}` is entirely server-owned, which would leave the PATCH endpoint nothing to patch |

The 20 `.jpg` files the original shipped under `wwwroot/pictures/` are **not** carried
here. The seed records their names, so `/pictures/1.jpg` names a file that was never
written; nothing in this API serves those bytes back, so nothing depends on them.

## Tests

```bash
mvn test
```

133 tests: integration tests over `MockMvc` with at least one per row of both tables above,
plus `FileStorageServiceTest`, which drives the storage budget directly rather than through
thousands of requests. They are the safety net and they also document the contract.

## Layout

```
src/main/java/org/menu/api/
  config/       OpenApiConfig, JacksonConfig, WebConfig, MultipartConfig
  controller/   the five controllers, ApiExceptionHandler, MultipartParts
  domain/       Category, Food, FoodPicture              (JPA entities)
  dto/          what the API speaks, including the form/ classes that carry
                the multipart schemas
  mapper/       ModelsMapper
  repository/   Spring Data JPA
  service/      the business rules, the file rules, the patch rules, the seed
src/main/resources/setup/                                (categories.csv, dishes.csv)
```

The jar is named `menu-api-sut.jar` and the base package is `org.menu.api`, both of which
are what an EvoMaster driver would expect. No driver lives here: this repository is the
application only.
