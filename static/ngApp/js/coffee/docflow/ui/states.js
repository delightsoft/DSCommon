(function() {
  var addUrlArgSupport, module;

  module = angular.module('docflow.ui.states', ['ui.router.state', 'docflow.config']);

  module.config([
    '$stateProvider', '$docflowConfigProvider', (function($stateProvider, $docflowConfigProvider) {
      var doc, docType, _fn, _ref;
      $stateProvider.state({
        name: "doc",
        url: "/doc",
        templateUrl: (function() {
          return "/tmpl/" + $docflowConfigProvider.templateBase + "/doc/docIndex?" + $docflowConfigProvider.tmplParams;
        })
      });
      _ref = $docflowConfigProvider.docs;
      _fn = (function(_this) {
        return function(docType) {
          var formEditCreateController, state;
          $stateProvider.state({
            name: "doc." + docType
          });
          $stateProvider.state({
            url: "/" + docType,
            name: "doc." + docType + ".list",
            views: {
              '@doc': {
                templateUrl: (function() {
                  return "/tmpl/doc/" + docType + "?b=" + $docflowConfigProvider.templateBase + "&t=list&" + $docflowConfigProvider.tmplParams;
                }),
                controller: [
                  '$scope', (function($scope) {
                    $scope.values = {};
                  })
                ]
              }
            }
          });
          formEditCreateController = [
            '$scope', 'editor', '$docflowClient', (function($scope, editor, $docflowClient) {
              $scope.itemHeaderUrl = "/tmpl/doc/" + docType + "?b=" + $docflowConfigProvider.templateBase + "&t=formTitle&" + $docflowConfigProvider.tmplParams;
              $scope.editor = editor;
              editor.controller($scope);
            })
          ];
          $stateProvider.state({
            name: "doc." + docType + ".create",
            url: "/" + docType + "/new",
            resolve: {
              editor: [
                '$docflowEditor', '$stateParams', '$location', '$log', (function($docflowEditor, $stateParams, $location, $log) {
                  var e;
                  try {
                    return addUrlArgSupport(new $docflowEditor("" + docType), $location, $log).loadOrCreate();
                  } catch (_error) {
                    e = _error;
                    $log.error(e);
                    throw e;
                  }
                })
              ]
            },
            views: {
              '@doc': {
                templateUrl: (function() {
                  return "/tmpl/doc/" + docType + "?b=" + $docflowConfigProvider.templateBase + "&t=form&" + $docflowConfigProvider.tmplParams;
                }),
                controller: formEditCreateController
              }
            }
          });
          $stateProvider.state({
            name: "doc." + docType + ".form",
            url: "/" + docType + "/{id}",
            resolve: {
              editor: [
                '$docflowEditor', '$stateParams', '$location', '$log', (function($docflowEditor, $stateParams, $location, $log) {
                  var e;
                  try {
                    return addUrlArgSupport(new $docflowEditor("" + docType + "@" + $stateParams.id), $location, $log).loadOrCreate();
                  } catch (_error) {
                    e = _error;
                    $log.error(e);
                    throw e;
                  }
                })
              ]
            },
            views: {
              '@doc': {
                templateUrl: (function() {
                  return "/tmpl/doc/" + docType + "?b=" + $docflowConfigProvider.templateBase + "&t=form&" + $docflowConfigProvider.tmplParams;
                }),
                controller: formEditCreateController
              }
            }
          });
          state = $stateProvider.state({
            name: "doc." + docType + ".form.comments",
            url: "/comments"
          });
          state["@doc." + docType + ".form"] = {
            templateUrl: (function() {
              return "/tmpl/comments/itemComments?b=" + $docflowConfigProvider.templateBase + "&" + $docflowConfigProvider.tmplParams;
            })
          };
        };
      })(this);
      for (docType in _ref) {
        doc = _ref[docType];
        _fn(docType);
      }
    })
  ]);

  addUrlArgSupport = (function(editor, $location, $log) {
    var superController, superSelectTab;
    superController = editor.controller;
    editor.controller = (function($scope) {
      editor.selectTab($location.search().tab, true);
      superController.call(editor, $scope);
    });
    superSelectTab = editor.selectTab;
    editor.selectTab = (function(tabName, safe) {
      var newTab, oldTab, s;
      s = $location.search();
      oldTab = s.tab;
      superSelectTab.call(editor, tabName, safe);
      newTab = editor.selectedTab.name;
      if ((newTab === '_main' && angular.isDefined(oldTab)) || newTab !== oldTab) {
        if (newTab === '_main') {
          delete s.tab;
        } else {
          s.tab = newTab;
        }
        $location.search(s);
        $location.replace();
      }
    });
    return editor;
  });

}).call(this);
