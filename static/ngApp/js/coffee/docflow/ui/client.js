(function() {
  var module;

  module = angular.module('docflow.ui.client', ['docflow.config', 'docflow.ui.utils', 'docflow.ui.httpListener', 'docflow.ui.actions']);

  module.factory('$docflowClient', [
    '$docflowActions', '$docflowUtils', '$docflowConfig', '$http', '$rootScope', '$document', '$q', (function($docflowActions, $docflowUtils, $docflowConfig, $http, $rootScope, $document, $q) {
      var createdDoc, currentAction, formDoc;
      currentAction = null;
      createdDoc = null;
      formDoc = null;
      return {
        list: (function(options) {}),
        get: (function(id, options) {
          var deferred, p, query, t, url, v, _ref;
          if ((createdDoc != null ? createdDoc.id : void 0) === id) {
            _ref = [createdDoc, null], t = _ref[0], createdDoc = _ref[1];
            deferred = $q.defer();
            deferred.resolve(t);
            return deferred.promise;
          }
          createdDoc = null;
          query = [];
          if (options) {
            for (p in options) {
              v = options[p];
              if (angular.isDefined(v)) {
                query.push("" + p + "=" + v);
              }
            }
          }
          url = "/api/get/" + id + "?" + (query.join('&')) + "&" + $docflowConfig.apiParams;
          return $http({
            method: 'GET',
            url: url
          }).then((function(resp) {
            return resp.data;
          }), (function(resp) {
            throw Error("Failed to get document with id '" + id + "' (Code: " + resp.status + ")");
          }));
        }),
        action: (function(id, name, options) {
          var actionConfig, data, deferred, docConfig, p, query, showMessage, url, v, _ref;
          docConfig = $docflowConfig.docs[$docflowUtils.docType(id)];
          if (!docConfig) {
            throw Error("Doctype " + ($docflowUtils.docType(id)) + " not in docflowConfig");
          }
          actionConfig = (_ref = docConfig.actions) != null ? _ref[name] : void 0;
          if (!actionConfig) {
            throw Error("Doctype " + ($docflowUtils.docType(id)) + " has no action " + name + " in docflowConfig");
          }
          if (currentAction) {
            if (typeof currentAction.forceShowMessage === "function") {
              currentAction.forceShowMessage();
            }
            return $q.reject('Another action is currenly running', currentAction);
          }
          query = [];
          for (p in options) {
            v = options[p];
            if (!(p === 'params' || p === 'doc' || p === 'message')) {
              if (angular.isDefined(v)) {
                query.push("" + p + "=" + v);
              }
            }
          }
          url = "/api/action/" + id + "/" + name + "?" + (query.join('&')) + "&" + $docflowConfig.apiParams;
          if (options) {
            data = {};
            if (options.params) {
              data.params = options.params;
            }
            if (options.doc) {
              data.doc = options.doc;
            }
            showMessage = !angular.isDefined(options.message) || options.message;
          } else {
            showMessage = true;
          }
          if (showMessage) {
            deferred = $q.defer();
            $docflowActions.send(currentAction = new $docflowActions.Action(id, name, deferred.promise));
          }
          return $http({
            method: 'POST',
            url: url,
            data: data
          }).then((function(resp) {
            data = resp.data;
            if (data.code === 'Ok') {
              if (name === 'create') {
                createdDoc = data.doc;
              }
              if (data.file) {
                $("<iframe>").hide().prop("src", "/api/download/" + data.file).appendTo("body");
              }
              if (deferred) {
                deferred.resolve(data.message);
                currentAction = null;
              }
              return data;
            } else {
              if (deferred) {
                deferred.reject(data.message);
                currentAction = null;
              }
              return $q.reject(resp);
            }
          }), (function(resp) {
            if (deferred) {
              deferred.reject();
              currentAction = null;
            }
          }));
        })
      };
    })
  ]);

}).call(this);
