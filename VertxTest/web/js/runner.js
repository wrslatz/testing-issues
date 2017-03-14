var EB;
var URL;
var APICall = "api/eventbus/publish/";
var IncomingAddress = "heartbeat-test";
var OutgoingAddress = "client-test";


$(document).ready(function () {
    
});

function createConnection(){
    URL = $("#serveraddressinput").val();
    console.log("Creating Eventbus connection at " + URL + "eventbus");
    EB = new EventBus(URL + "eventbus");
    
    testAPICall();
    
    EB.onopen = function(){
        console.log("Eventbus connection successfully made at " + URL + "eventbus");
        
        setStatusGood();
        
        console.log("Registering Eventbus handler for messages at " + IncomingAddress);
        EB.registerHandler(IncomingAddress, function(error, message){
            console.log("Received Eventbus message " + JSON.stringify(message));
            var text = $("#incoming").text();
            var time = new Date();
            $("#incoming").text(time + " - " + message.body + "\n" + text);
        });
    };
    
    EB.onclose = function(){
        console.log("Eventbus connection at " + URL + " has been lost");
        URL = "";
        setStatusBad();
    };
}

function testAPICall(){
    var link = URL + APICall + "heartbeat-test";
    console.log("Testing API call to " + link);
    $.ajax({
        url: link,
        type: 'POST',
        data: JSON.stringify({"testFromClient": "Test message sent from Client via API Call"}),
        dataType: 'json',
        success: function (data, textStatus) {
            console.log("API Call Success: " + JSON.stringify(data));
        },
        error: function (request, error) {
            console.log("API Call ERROR: " + JSON.stringify(request) + " " + error);
        }
    });
}

function setStatusGood(){
    $("#serverstatus").text("Connected!");
    $("#messagecenter").css('display', 'block');
}

function setStatusBad(){
    $("#serverstatus").text("Disconnected!");
    $("#messagecenter").css('display', 'none');
}

function sendTestMessage(){
    console.log("Sending test message to address " + OutgoingAddress);
    EB.send(OutgoingAddress, "Testing 1, 2, 3...");
}