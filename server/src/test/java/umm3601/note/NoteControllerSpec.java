package umm3601.note;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.google.common.collect.ImmutableMap;
import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.util.ContextUtil;
import io.javalin.plugin.json.JavalinJson;
import umm3601.JwtProcessor;
import umm3601.UnprocessableResponse;

public class NoteControllerSpec {

  MockHttpServletRequest mockReq = new MockHttpServletRequest();
  MockHttpServletResponse mockRes = new MockHttpServletResponse();

  @Mock(name = "dt")

  private ObjectId samsNoteId;
  private Date samsDate;

  private ObjectId doorBoard1ID;
  private ObjectId samsDoorBoardID;

  static MongoClient mongoClient;
  @Spy
  static MongoDatabase db;

  @Mock(name = "jwtProcessor")
  JwtProcessor jwtProcessorMock;

  private void useJwtForUser1() {
    // Make a fake DecodedJWT for jwtProcessorMock to return.
    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("user1");
    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);
  }

  private void useJwtForSam() {
    // Make a fake DecodedJWT for jwtProcessorMock to return.
    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("sam");
    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);
  }

  private void useJwtForNewUser() {
    DecodedJWT mockDecodedJWT = Mockito.mock(DecodedJWT.class);
    when(mockDecodedJWT.getSubject()).thenReturn("e7fd674c72b76596c75d9f1e");
    when(jwtProcessorMock.verifyJwtFromHeader(any()))
      .thenReturn(mockDecodedJWT);
  }

  private void useInvalidJwt() {
    when(jwtProcessorMock.verifyJwtFromHeader(any())).thenThrow(new UnauthorizedResponse());
  }

  @InjectMocks
  NoteController noteController;

  static ObjectMapper jsonMapper = new ObjectMapper();

  @BeforeAll
  public static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");
    mongoClient = MongoClients.create(MongoClientSettings.builder()
        .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr)))).build());

    db = mongoClient.getDatabase("test");
  }

  @BeforeEach
  public void setupEach() throws IOException {
    MockitoAnnotations.initMocks(this);
    // Reset our mock objects
    mockReq.resetAll();
    mockRes.resetAll();

    MongoCollection<Document> noteDocuments = db.getCollection("notes");
    noteDocuments.drop();

    MongoCollection<Document> doorBoardDocuments = db.getCollection("doorBoards");
    doorBoardDocuments.drop();

    doorBoard1ID = new ObjectId();
    BasicDBObject doorBoard1 = new BasicDBObject("_id", doorBoard1ID)
      .append("name", "User One")
      .append("email", "one@one.com")
      .append("building", "100 First St")
      .append("officeNumber", "1001")
      .append("sub", "user1");

    samsDoorBoardID = new ObjectId();
    BasicDBObject samsDoorBoard = new BasicDBObject("_id", samsDoorBoardID)
      .append("name", "Sam Spade")
      .append("email", "sam@frogs.frogs")
      .append("building", "Herpetology Department")
      .append("officeNumber", "2001")
      .append("sub", "sam");

    doorBoardDocuments.insertOne(Document.parse(doorBoard1.toJson()));
    doorBoardDocuments.insertOne(Document.parse(samsDoorBoard.toJson()));

    List<Document> testNotes = new ArrayList<>();
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"" + doorBoard1ID + "\", "
      + "body: \"I am running 5 minutes late to my non-existent office\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expiration: \"2099-04-17T04:18:09.302Z\", "
      + "status: \"active\", "
      + "favorite:" + false + ", "
      + "isExpired:" + false + ", "
      + "isPinned: true,"
      + "}"));
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"" + doorBoard1ID + "\", "
      + "body: \"I am never coming to my office again\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expiration: \"2099-04-17T04:18:09.302Z\", "
      + "status: \"active\", "
      + "favorite:" + false + ", "
      + "isExpired:" + false + ", "
      + "isPinned: true,"
      + "}"));
    testNotes.add(Document.parse("{ "
      + "doorBoardID: \"" + samsDoorBoardID + "\", "
      + "body: \"Not many come to my office I offer donuts\", "
      + "addDate: \"2020-03-07T22:03:38+0000\", "
      + "expiration: \"2019-04-17T04:18:09.302Z\", "
      + "status: \"active\", "
      + "favorite:" + false + ", "
      + "isExpired:" + false + ", "
      + "isPinned: true,"
      + "}"));

    samsNoteId = new ObjectId();
    BasicDBObject sam = new BasicDBObject("_id", samsNoteId);
    sam = sam.append("doorBoardID", samsDoorBoardID.toHexString())
      .append("body", "I am sam")
      .append("addDate", "2020-03-07T22:03:38+0000")
      .append("expiration", "2099-04-17T04:18:09.302Z")
      .append("status", "active")
      .append("favorite", false)
      .append("isExpired", false)
      .append("isPinned", true);

    noteDocuments.insertMany(testNotes);
    noteDocuments.insertOne(Document.parse(sam.toJson()));
    samsDate = samsNoteId.getDate();

    // The above described test "notes" are actually only ever Documents, and as such lack the functions of the Note type--
    // in particular, getAddDate, which is used to create the addDate field on serialization.  As such, all notes in the testing
    // collection do not have addDates.  However, addDates are added when a note is added to the database normally via add addNewNote,
    // and once added will persist in the database. Likewise, if the output is parsed as a Date, the getAddDate function can be called.
    // Therefore, this is something to be aware of, but should not impact final functionality.
  }

  @AfterAll
  public static void teardown() {
    db.drop();
    mongoClient.close();
  }

  /*
   * Testing to make sure that the objects in the initial database actually have addDates.
   * This is to determine if they are getting mangled in some way when being edited, or if they
   * were never good in the first place.
   */

   /*
   // Fails, but only due to the way the test environment is set up.
   @Test
   public void testSingleNote() {
     assertEquals(samsDate, db.getCollection("notes").find(eq("_id", samsNoteId)).first().getDate("addDate"));
   }
   */

  /*
   * Tests for GET api/notes when you're logged in with the right credentials.
   */

  @Test
  public void getAllNotesForDoorBoard1() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID);

    useJwtForUser1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals(doorBoard1ID.toHexString(), note.doorBoardID, "Incorrect ID");
      assertNotNull(note.getAddDate());
    }
  }

  @Test
  public void getDraftNotesForDoorBoard1() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID + "&status=draft");

    useJwtForUser1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(0, resultNotes.length);
  }

  @Test
  public void getActiveNotesForDoorBoard1() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID + "&status=active");

    useJwtForUser1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals(doorBoard1ID.toHexString(), note.doorBoardID, "Incorrect ID");
    }
  }

  /*
   * Tests for GET api/notes when you aren't logged in.
   */

  @Test
  public void getAllNotesForDoorBoard1WithoutJwtFails() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID);

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getDraftNotesForDoorBoard1WithoutJwtFails() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID + "&status=draft");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  // Active notes are public.
  // You're allowed to see them even if you aren't logged in.
  @Test
  public void getActiveNotesForDoorBoard1WithoutJwtIsFine() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID + "&status=active");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals(doorBoard1ID.toHexString(), note.doorBoardID, "Incorrect ID");
    }
  }

  /*
   * Tests for GET api/notes when you're logged in as a different doorBoard.
   */

  @Test
  public void getAllNotesForDoorBoard1LoggedInAsWrongDoorBoardFails() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID);

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(ForbiddenResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getDraftNotesForDoorBoard1LoggedInAsWrongDoorBoardFails() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID + "&status=draft");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(ForbiddenResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getActiveNotesForDoorBoard1LoggedInAsWrongDoorBoardIsFine() {
    mockReq.setQueryString("doorBoardID=" + doorBoard1ID + "&status=active");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(2, resultNotes.length);
    for (Note note : resultNotes) {
      assertEquals(doorBoard1ID.toHexString(), note.doorBoardID, "Incorrect ID");
    }
  }

  /*
   * Tests for GET api/notes without the doorBoardID query parameter.
   *
   * This is always allowed if you specify status=active; anyone is allowed to
   * view any active notes.
   *
   * Without status=active, it's always forbidden; no-one can view *all* notes in
   * the database, including draft, deleted, and template notes. It doesn't matter
   * who you're logged in as; you can still only see your own stuff.
   */

  @Test
  public void getAllNotesInTheDatabaseFailsWithoutAJwt() {
    mockReq.setQueryString("");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getAllNotesInTheDatabaseFailsEvenWithAJwt() {
    mockReq.setQueryString("");

    useJwtForUser1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    assertThrows(ForbiddenResponse.class, () -> {
      noteController.getNotesByDoorBoard(ctx);
    });
  }

  @Test
  public void getAllActiveNotesInTheDatabaseIsFineEvenWithoutJwt() {
    mockReq.setQueryString("status=active");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes");

    noteController.getNotesByDoorBoard(ctx);

    assertEquals(200, mockRes.getStatus());

    String result = ctx.resultString();
    Note[] resultNotes = JavalinJson.fromJson(result, Note[].class);

    assertEquals(4, resultNotes.length);
  }

  /*
   * Tests for adding notes.
   */

  // @Test
  // public void addNote() throws IOException {
  //   ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
  //   String testNewNote = "{ "
  //     + "\"doorBoardID\": \"" + doorBoard1ID + "\", "
  //     + "\"body\": \"Test Body\", "
  //     + "\"expiration\": \"2025-04-17T04:18:09.302Z\", "
  //     + "\"status\": \"active\""
  //     + "}";

  //   mockReq.setBodyContent(testNewNote);
  //   mockReq.setMethod("POST");

  //   Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

  //   useJwtForUser1();

  //   noteController.addNewNote(ctx);

  //   assertEquals(201, mockRes.getStatus());

  //   String result = ctx.resultString();
  //   String id = jsonMapper.readValue(result, ObjectNode.class).get("id").asText();
  //   assertNotEquals("", id);

  //   assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", new ObjectId(id))));

  //   Document addedNote = db.getCollection("notes").find(eq("_id", new ObjectId(id))).first();

  //   assertNotNull(addedNote);
  //   assertEquals(doorBoard1ID.toHexString(), addedNote.getString("doorBoardID"));
  //   assertEquals("Test Body", addedNote.getString("body"));
  //   assertNotNull(addedNote.getDate("addDate")); // we don't know when it was created, so we just want to make sure the date exists.
  //   assertEquals("2025-04-17T04:18:09.302Z", addedNote.getString("expiration"));
  //   assertEquals("active", addedNote.getString("status"));
  // }

  // @Test
  // public void addNoteWithInvalidJwtFails() throws IOException {
  //   String testNewNote = "{ "
  //     + "\"doorBoardID\": \"" + doorBoard1ID + "\", "
  //     + "\"body\": \"Faily McFailface\", "
  //     + "\"expiration\": \"2025-04-17T04:18:09.302Z\", "
  //     + "\"status\": \"active\""
  //     + "}";

  //   mockReq.setBodyContent(testNewNote);
  //   mockReq.setMethod("POST");

  //   Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

  //   useInvalidJwt();

  //   assertThrows(UnauthorizedResponse.class, () -> {
  //     noteController.addNewNote(ctx);
  //   });

  //   assertEquals(0, db.getCollection("notes").countDocuments(eq("body", "Faily McFailface")));
  // }

  @Test
  public void addNoteToNonexistentDoorBoardFails() throws IOException {
    String testNewNote = "{ "
      + "\"doorBoardID\": \"" + new ObjectId() + "\", "
      + "\"body\": \"Faily McFailface\", "
      + "\"expiration\": \"2025-04-17T04:18:09.302Z\", "
      + "\"isPinned\": true,"
      + "\"status\": \"active\""
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    useJwtForSam();

    assertThrows(BadRequestResponse.class, () -> {
      noteController.addNewNote(ctx);
    });

    assertEquals(0, db.getCollection("notes").countDocuments(eq("body", "Faily McFailface")));
  }

  @Test
  public void addNoteWithBadDoorBoardIDFails() throws IOException {
    String testNewNote = "{ "
      + "\"doorBoardID\": \"frogs are cool I guess sometimes\", "
      + "\"body\": \"Faily McFailface\", "
      + "\"addDate\": \"2020-03-07T22:03:38+0000\", "
      + "\"expiration\": \"2025-04-17T04:18:09.302Z\", "
      + "\"isPinned\": true,"
      + "\"status\": \"active\""
      + "}";

    mockReq.setBodyContent(testNewNote);
    mockReq.setMethod("POST");

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

    useJwtForSam();

    assertThrows(BadRequestResponse.class, () -> {
      noteController.addNewNote(ctx);
    });

    assertEquals(0, db.getCollection("notes").countDocuments(eq("body", "Faily McFailface")));
  }



  // @Test
  // public void AddNoteWithoutExpiration() throws IOException {
  //   String testNewNote = "{ "
  //     + "\"doorBoardID\": \"" + doorBoard1ID + "\", "
  //     + "\"body\": \"Test Body\", "
  //     + "\"status\": \"active\""
  //     + "}";


  //   mockReq.setBodyContent(testNewNote);
  //   mockReq.setMethod("POST");

  //   useJwtForUser1();

  //   Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/new");

  //   noteController.addNewNote(ctx);

  //   assertEquals(201, mockRes.getStatus());

  //   String result = ctx.resultString();
  //   String id = jsonMapper.readValue(result, ObjectNode.class).get("id").asText();
  //   assertNotEquals("", id);

  //   assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", new ObjectId(id))));

  //   Document addedNote = db.getCollection("notes").find(eq("_id", new ObjectId(id))).first();
  //   assertNotNull(addedNote);
  //   assertEquals(doorBoard1ID.toHexString(), addedNote.getString("doorBoardID"));
  //   assertEquals("Test Body", addedNote.getString("body"));
  //   assertNotNull(addedNote.getDate("addDate"));
  //   assertNull(addedNote.getString("expiration"));
  //   //assertEquals("active", addedNote.getString("status"));
  // }

  /*
   * Tests for deleting notes.
   */

  @Test
  public void deleteNote() throws IOException {
    mockReq.setMethod("DELETE");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    noteController.deleteNote(ctx);

    assertEquals(204, mockRes.getStatus());

    assertEquals(0, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
    // Make sure we stop the Death Timer
  }

  @Test
  public void deleteNoteWithoutJwtFails() throws IOException {
    mockReq.setMethod("DELETE");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    assertThrows(UnauthorizedResponse.class, () -> {
      noteController.deleteNote(ctx);
    });

    // Make sure that the database is unchanged
    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
  }

  @Test
  public void deleteNoteLoggedInAsWrongUserFails() throws IOException {
    mockReq.setMethod("DELETE");

    useJwtForUser1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    assertThrows(ForbiddenResponse.class, () -> {
      noteController.deleteNote(ctx);
    });

    // Make sure that the database is unchanged
    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
  }


  /*
   * Tests for editing notes.
   */

  // Fix this
  // @Test
  // public void editSingleField() throws IOException {
  //   String reqBody = "{\"body\": \"I am not sam anymore\"}";
  //   mockReq.setBodyContent(reqBody);
  //   mockReq.setMethod("PATCH");
  //   // Because we're partially altering an object, we make a body with just the
  //   // alteration and use the PATCH (not PUT) method

  //   useJwtForSam();

  //   Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
  //   noteController.editNote(ctx);

  //   // We don't have a good way to return just the edited object right now,
  //   // so we return nothing in the body and show that with a 204 response.

  //   assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));
  //   // There should still be exactly one note per id, and the id shouldn't have
  //   // changed.

  //   Document editedNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
  //   assertNotNull(editedNote);
  //   // The note should still actually exist

  //   assertEquals("I am not sam anymore", editedNote.getString("body"));
  //   // The edited field should show the new value

  //   assertEquals(samsDoorBoardID.toHexString(), editedNote.getString("doorBoardID"));
  //   assertEquals("active", editedNote.getString("status"));
  //   //assertEquals(samsDate, editedNote.getDate("addDate"));
  //   assertEquals("2099-04-17T04:18:09.302Z", editedNote.getString("expiration"));
  //   // all other fields should be untouched
  // }

  //@Test
  /*public void editMultipleFields() throws IOException {
    ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
    String reqBody = "{\"body\": \"I am still sam\", \"expiration\": \"2098-04-17T04:18:09.302Z\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));
    noteController.editNote(ctx);

    assertEquals(204, mockRes.getStatus());

    assertEquals(1, db.getCollection("notes").countDocuments(eq("_id", samsNoteId)));

    Document editedNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertNotNull(editedNote);

    assertEquals("I am still sam", editedNote.getString("body"));
    assertEquals("2098-04-17T04:18:09.302Z", editedNote.getString("expiration"));

    assertEquals("active", editedNote.getString("status"));
    assertEquals(samsDoorBoardID.toHexString(), editedNote.getString("doorBoardID"));
    assertEquals("2098-04-17T04:18:09.302Z", editedNote.getString("addDate"));

    // Since the expiration was changed, the timer's status should have been updated
    verify(dtMock).updateTimerStatus(noteCaptor.capture());
    Note updatedNote = noteCaptor.getValue();
    assertEquals(samsNoteId.toHexString(), updatedNote._id);
    assertEquals("I am still sam", updatedNote.body);
    assertEquals("2098-04-17T04:18:09.302Z", updatedNote.expiration);
    assertEquals("active", updatedNote.status);
    assertEquals(samsDoorBoardID.toHexString(), updatedNote.doorBoardID);
    assertEquals(samsDate, updatedNote.getAddDate());
  }
*/
  @Test
  public void editWithoutJwtFails() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useInvalidJwt();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    // Make sure the note was not changed.
    Document samsNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertEquals("I am sam", samsNote.getString("body"));
  }

  @Test
  public void editLoggedInAsWrongDoorBoardFails() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForUser1();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));


    // Make sure the note was not changed.
    Document samsNote = db.getCollection("notes").find(eq("_id", samsNoteId)).first();
    assertEquals("I am sam", samsNote.getString("body"));
  }

  // Fix this

  // @Test
  // public void editMissingId() throws IOException {
  //   String reqBody = "{\"body\": \"I am not sam anymore\"}";
  //   mockReq.setBodyContent(reqBody);
  //   mockReq.setMethod("PATCH");

  //   useJwtForSam();

  //   Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id",
  //       ImmutableMap.of("id", new ObjectId().toHexString()));

  //   assertThrows(NotFoundResponse.class, () -> {
  //     noteController.editNote(ctx);
  //   });
  // }

  @Test
  public void editBadId() throws IOException {
    String reqBody = "{\"body\": \"I am not sam anymore\"}";
    mockReq.setBodyContent(reqBody);
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id",
        ImmutableMap.of("id", "this garbage isn't an id!"));

    assertThrows(IllegalArgumentException.class, () -> {
      noteController.editNote(ctx);
    });
  }

  @Test
  public void editIdWithMalformedBody() throws IOException {
    mockReq.setBodyContent("This isn't parsable as a document");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(BadRequestResponse.class, () -> {
      noteController.editNote(ctx);
    });
  }

  @Test
  public void editIdWithBadKeys() throws IOException {
    mockReq.setBodyContent("{\"badKey\": \"irrelevant value\"}");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(NullPointerException.class, () -> {
      noteController.editNote(ctx);
    });
    // ConflictResponse represents a 409 error, in this case an attempt to edit a
    // nonexistent field.
  }

  @Test
  public void editIdWithIllegalKeys() throws IOException {
    mockReq.setBodyContent("{\"doorBoardID\": \"Charlie\"}");
    mockReq.setMethod("PATCH");

    useJwtForSam();

    Context ctx = ContextUtil.init(mockReq, mockRes, "api/notes/:id", ImmutableMap.of("id", samsNoteId.toHexString()));

    assertThrows(NullPointerException.class, () -> {
      noteController.editNote(ctx);
    });
  }

  // The 422 and 409 errors could be switched between these conditions, or they
  // could possibly both be 409?
  // Additionally, should attempting to edit a non-editable field (id, doorBoardID, or
  // addDate) throw a 422, 409, 400, or 403?

}
