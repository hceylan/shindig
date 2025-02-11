/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * @fileoverview Tests for actions feature
 */

function DeclarativeActionsTest(name) {
  TestCase.call(this, name);
}

DeclarativeActionsTest.inherits(TestCase);

(function() {
  
DeclarativeActionsTest.prototype.setUp = function() {
    this.apiUri = window.__API_URI;
    window.__API_URI = shindig.uri('http://shindig.com');
    this.containerUri = window.__CONTAINER_URI;
    window.__CONTAINER_URI = shindig.uri('http://container.com');

    this.gadgetsRpc = gadgets.rpc;
    var that = this;
    gadgets.rpc = {};
    gadgets.rpc.register = function() {};
    gadgets.rpc.call = function() {
      that.rpcArguments = Array.prototype.slice.call(arguments);
    };
};

DeclarativeActionsTest.prototype.tearDown = function() {
    window.__API_URI = this.apiUri;
    window.__CONTAINER_URI = this.containerUri;
    
    gadgets.rpc = this.gadgetsRpc;
    this.rpcArguments = undefined; 
};

DeclarativeActionsTest.prototype.testGadgetsAddAction = function() {
  var actionId = "testAction";
  var callbackFn = function(){};
  var _actionObj = {
      id: actionId,
      label:"Test Action",
      path:"container/navigationLinks",
      callback: callbackFn
    };
  gadgets.actions.addAction(_actionObj);
  this.assertRpcCalled('..', 'actions', null, 'bindAction', _actionObj);
};

DeclarativeActionsTest.prototype.testGadgetsRemoveAction = function() {
  var actionId = "testAction";
  gadgets.actions.removeAction(actionId);
  this.assertRpcCalled('..', 'actions', null, 
      'removeAction', actionId);
};


DeclarativeActionsTest.prototype.testContainerGetAction = function() {
  var container = new osapi.container.Container({});
  var actionId = "testAction";
  var actionObj = container.actions.getAction(actionId);
  // registry is empty
  this.assertUndefined(actionObj);
};


DeclarativeActionsTest.prototype.testContainerGetActionsByPath = function() {
  var container = new osapi.container.Container();
  var actionId = "testAction";
  var actionsArray = container.actions
    .getActionsByPath("container/navigationLinks");
  //registry is empty
  this.assertEquals(actionsArray, []);
};

DeclarativeActionsTest.prototype.testContainerGetActionsByDataType = 
  function(){
    var container = new osapi.container.Container();
    var actionId = "testAction";
    var actionsArray = container.actions
      .getActionsByDataType("opensocial.Person");
    //registry is empty
    this.assertEquals(actionsArray, []);
  };

/**
 * Uncomment following _Full tests once addAction() and removeAction() 
 * functions in actions_container.js are uncommented
 */
/* FULL TESTS
DeclarativeActionsTest.prototype.testContainerGetAction_Full = function() {
  var container = new osapi.container.Container({});
  var actionId = "testAction";
  var actionObj_ = {
          id: actionId,
          label: "Test Action",
          path: "container/navigationLinks"
  }
  container.actions.addAction(actionObj_);
  var actionObj = container.actions.getAction(actionId);
  this.assertEquals(actionObj_, actionObj);
  
  container.actions.removeAction(actionId);
  actionObj = container.actions.getAction(actionId);
  this.assertUndefined(actionObj);

};

DeclarativeActionsTest.prototype.testContainerGetActions_Full = function() {
  var container = new osapi.container.Container({});
  var actionId = "testAction";
  var actions = [{
    id: "test1",
    label: "Test Action1",
    path: "container/navigationLinks"
  },
  {
    id: "test2",
    label: "Test Action2",
    path: "container/navigationLinks"
  },
  {
    id: "test3",
    label: "Test Action3",
    dataType: "opensocial.Person"
  },
  {
    id: "test4",
    label: "Test Action4",
    dataType: "opensocial.Person"
  }
  ];
  for (actionIndex in actions) {
    container.actions.addAction(actions[actionIndex]);
  }
  
  var allActions = container.actions.getAllActions();
  this.assertEquals(actions, allActions);
  
  for (actionIndex in actions) {
    container.actions.removeAction(actions[actionIndex].id);
  }
  
  allActions = container.actions.getAllActions();
  this.assertEquals([], allActions);

};
DeclarativeActionsTest.prototype.testContainerGetActionsByPath_Full = 
  function(){
    var container = new osapi.container.Container();
    var actionId = "testAction";
    var actionObj_ = {
            id: actionId,
            label: "Test Action",
            path: "container/navigationLinks"
    }
    container.actions.addAction(actionObj_);
    var actionsArray = container.actions
      .getActionsByPath("container/navigationLinks");
    this.assertEquals(actionsArray, [ actionObj_ ]);
    
    container.actions.removeAction(actionId);
    actionsArray = container.actions
      .getActionsByPath("container/navigationLinks");
    this.assertEquals(actionsArray, []);
  };

DeclarativeActionsTest.prototype.testContainerGetActionsByDataType_Full = 
  function() {
    var container = new osapi.container.Container();
    var actionId = "testAction";
    var actionObj_ = {
            id: actionId,
            label: "Test Action",
            dataType: "opensocial.Person"
    };
    
    container.actions.addAction(actionObj_);
    var actionsArray = container.actions
      .getActionsByDataType("opensocial.Person");
    this.assertEquals([ actionObj_ ], actionsArray);
    
    container.actions.removeAction(actionId);
    actionsArray = container.actions
      .getActionsByDataType("opensocial.Person");
    this.assertEquals([], actionsArray);
  
  };

 FULL TESTS */

/**
 * Asserts gadgets.rpc.call() is called with the expected arguments given.
 */
DeclarativeActionsTest.prototype.assertRpcCalled = function() {
  this.assertNotUndefined("RPC was not called.", this.rpcArguments);
  this.assertEquals("RPC argument list not valid length.", arguments.length,
      this.rpcArguments.length);

  for ( var i = 0; i < arguments.length; i++) {
    this.assertEquals(arguments[i], this.rpcArguments[i]);
  }
  this.rpcArguments = undefined;
};

})();