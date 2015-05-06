(function() {
  var module;

  module = angular.module('docflow', ['docflow.ui.client', 'docflow.ui.utils', 'docflow.config', 'ui.bootstrap.modal']);

  module.run([
    '$docflow', '$state', '$rootScope', (function($docflow, $state, $rootScope) {
      $rootScope.$docflow = $docflow;
      $rootScope.$state = $state;
    })
  ]);

  module.factory('$docflow', [
    '$docflowClient', '$docflowConfig', '$docflowUtils', '$state', '$location', '$modal', '$window', '$q', '$log', (function($docflowClient, $docflowConfig, $docflowUtils, $state, $location, $modal, $window, $q, $log) {
      var editorConstructor;
      editorConstructor = null;
      return {
        _setEditor: (function(editor) {
          editorConstructor = editor;
        }),
        config: $docflowConfig,
        action: (function(id, name, options) {
          var action, deferred, doAction, doConfirm, doc, optName, params, paramsDialog, sid, val;
          sid = $docflowUtils.splitFullId(id);
          doc = $docflowConfig.docs[sid.docType];
          if (!doc) {
            throw Error("$docflow.action: Id '" + id + "': Unknown type '" + sid.docType + "'.");
          }
          action = doc.actions[name];
          if (!action) {
            throw Error("$docflow.action: Name '" + name + "': No such action in type '" + sid.docType + "'.");
          }
          if (action.service) {
            if (sid.id) {
              throw Error("$docflow.action: Action '" + sid.docType + "." + name + "': Service action requires type instead of id '" + id + "'.");
            }
          } else {
            if (!sid.id) {
              throw Error("$docflow.action: Action '" + sid.docType + "." + name + "': Non-service action requires id instead of type '" + id + "'.");
            }
          }
          if (options) {
            for (optName in options) {
              val = options[optName];
              switch (optName) {
                case 'params':
                  continue;
                case 'doc':
                  continue;
                case 'r':
                  continue;
                case 't':
                  continue;
                case 'tr':
                  continue;
                default:
                  $log.error("$docflow.action: Unexpected parameter '" + optName + "'.");
              }
            }
          } else {
            options = {};
          }
          deferred = $q.defer();
          doAction = (function(options) {
            var def;
            def = $q.defer();
            $docflowClient.action(id, name, options).then((function(r) {
              def.resolve(r);
            }), (function(r) {
              def.reject(r);
            }), (function(r) {
              def.notify(r);
            }));
            return def.promise;
          });
          doConfirm = null;
          paramsDialog = null;
          if (action.confirm) {
            doConfirm = (function(options, fromParams) {
              $modal.open({
                templateUrl: "/tmpl/" + $docflowConfig.templateBase + "/confirmation?" + $docflowConfig.tmplParams,
                backdrop: fromParams ? false : 'static',
                controller: [
                  '$scope', (function($scope) {
                    $scope.question = action.confirm;
                    $scope.$on('$stateChangeStart', (function() {
                      doConfirm.close();
                    }));
                  })
                ]
              }).result.then((function(r) {
                doAction(options).then((function(r) {
                  deferred.resolve(r);
                }), (function(r) {
                  deferred.reject(r);
                }), (function(r) {
                  deferred.notify(r);
                }));
              }), (function(r) {
                if (!paramsDialog) {
                  deferred.reject('Cancelled in confimation dialog');
                }
              }));
            });
          }
          if (action.params && !options.params) {
            params = angular.copy(action.params);
            paramsDialog = $modal.open({
              templateUrl: "/tmpl/doc/" + sid.docType + "?b=" + $docflowConfig.templateBase + "&t=params&a=" + name + "&" + $docflowConfig.tmplParams,
              backdrop: 'static',
              controller: [
                '$scope', (function($scope) {
                  $scope.item = params;
                  $scope.ok = (function() {
                    if (!options) {
                      options = {};
                    }
                    options.params = $docflowUtils.buildDocumentUpdate($scope.item);
                    if (doConfirm) {
                      doConfirm(options, true);
                    } else {
                      doAction(options).then((function(r) {
                        deferred.resolve(r);
                      }), (function(r) {}));
                    }
                  });
                  $scope.$on('$stateChangeStart', (function() {
                    paramsDialog.close();
                  }));
                })
              ]
            });
            deferred.promise.then((function(r) {
              paramsDialog.close(r);
            }), (function(r) {
              paramsDialog.close(r);
            }));
          } else if (doConfirm) {
            doConfirm(options, false);
          } else {
            doAction(options).then((function(r) {
              deferred.resolve(r);
            }), (function(r) {
              deferred.reject(r);
            }), (function(r) {
              deferred.notify(r);
            }));
          }
          return deferred.promise;
        }),
        openDoc: (function(docId, params) {
          var sid;
          sid = $docflowUtils.splitFullId(docId);
          if (!sid.id) {
            throw Error("Invalid id: " + fullId);
          }
          $state.transitionTo("doc." + sid.docType + ".form", {
            'id': sid.id
          });
          if (params != null ? params.replace : void 0) {
            $location.replace();
          }
        }),
        createDoc: (function(docType, params) {
          var sid;
          sid = $docflowUtils.splitFullId(docType);
          if (sid.id) {
            throw Error("Invalid type: " + fullId);
          }
          $state.transitionTo("doc." + sid.docType + ".create");
          if (params != null ? params.replace : void 0) {
            $location.replace();
          }
        }),
        hrefOpenDoc: (function(docId, params) {
          var sid;
          sid = $docflowUtils.splitFullId(docId);
          if (!sid.id) {
            throw Error("Invalid id: " + fullId);
          }
          return $state.href("doc." + sid.docType + ".form", {
            'id': sid.id
          });
        }),
        hrefCreateDoc: (function(docType, params) {
          var sid;
          sid = $docflowUtils.splitFullId(docType);
          if (sid.id) {
            throw Error("Invalid type: " + fullId);
          }
          return $state.transitionTo("doc." + sid.docType + ".create");
        }),
        openDocDialog: (function(docId, params) {
          var deferred, sid;
          sid = $docflowUtils.splitFullId(docId);
          if (!sid.id) {
            throw Error("Invalid id: " + fullId);
          }
          deferred = $q.defer();
          $q.when(new editorConstructor(docId, params).loadOrCreate()).then(function(editor) {
            var docDialog;
            docDialog = $modal.open({
              templateUrl: "/tmpl/doc/" + sid.docType + "?b=" + $docflowConfig.templateBase + "&t=formDialog&" + $docflowConfig.tmplParams,
              backdrop: 'static',
              controller: [
                '$scope', (function($scope) {
                  $scope.itemHeaderUrl = "/tmpl/doc/" + sid.docType + "?b=" + $docflowConfig.templateBase + "t=formTitle&" + $docflowConfig.tmplParams;
                  $scope.editor = editor;
                  editor.setEditorDialog(docDialog);
                  editor.controller($scope);
                  $scope.$on('$stateChangeStart', (function() {
                    docDialog.close();
                  }));
                })
              ]
            });
            docDialog.result.then((function(doc) {
              deferred.resolve(doc);
            }));
          });
          return deferred.promise;
        }),
        createDocDialog: (function(docType, params) {
          var deferred, sid;
          sid = $docflowUtils.splitFullId(docType);
          if (sid.id) {
            throw Error("Invalid type: " + fullId);
          }
          deferred = $q.defer();
          $q.when(new editorConstructor(docType, params).loadOrCreate()).then(function(editor) {
            var docDialog;
            docDialog = $modal.open({
              templateUrl: "/tmpl/doc/" + sid.docType + "?b=" + $docflowConfig.templateBase + "&t=formDialog&" + $docflowConfig.tmplParams,
              backdrop: 'static',
              controller: [
                '$scope', (function($scope) {
                  $scope.itemHeaderUrl = "/tmpl/doc/" + sid.docType + "?b=" + $docflowConfig.templateBase + "&t=formTitle&" + $docflowConfig.tmplParams;
                  $scope.editor = editor;
                  editor.setEditorDialog(docDialog);
                  editor.controller($scope);
                  $scope.$on('$stateChangeStart', (function() {
                    docDialog.close();
                  }));
                })
              ]
            });
            docDialog.result.then((function(doc) {
              deferred.resolve(doc);
            }));
          });
          return deferred.promise;
        }),
        navigateBack: (function() {
          $window.history.back();
        }),
        switchDemoUser: (function(demoUser) {
          var s;
          s = $location.search();
          if (angular.isDefined(demoUser)) {
            s.user = demoUser;
          } else {
            delete s.user;
          }
          $location.search(s);
          window.location.href = $location.$$url;
        })
      };
    })
  ]);

}).call(this);
