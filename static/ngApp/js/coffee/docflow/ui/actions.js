(function() {
  var module;

  module = angular.module('docflow.ui.actions', ['docflow.config']);

  module.constant('docflowActionInProgressShowAfterDelayTime', 2000);

  module.constant('docflowActionDoneHideDelayTime', 3000);

  module.directive('docflowActionsContainer', [
    '$docflowActions', '$compile', '$animate', (function($docflowActions, $compile, $animate) {
      return {
        restrict: 'A',
        scope: true,
        controller: [
          '$scope', (function($scope) {
            this.contentTransclude = null;
            this.contentElement = null;
          })
        ],
        link: (function($scope, element, attrs, ctrl) {
          var container, currentElement, messageScope, options;
          messageScope = null;
          currentElement = null;
          options = $scope.$eval(attrs.docflowActionsContainer);
          container = {
            errorOnly: !!(options != null ? options.errorOnly : void 0),
            setAction: (function(action) {
              $scope.action = action;
            })
          };
          $docflowActions.add(container);
          $scope.$watch('action.message', (function(message) {
            if (messageScope) {
              $animate.leave(currentElement);
              currentElement = null;
              messageScope.$destroy();
              messageScope = null;
            }
            if (message) {
              messageScope = $scope.$new();
              ctrl.contentTransclude(messageScope, (function(clone) {
                currentElement = clone;
                currentElement.html(message);
                $compile(currentElement.contents())(messageScope);
                $animate.enter(currentElement, null, ctrl.contentElement);
              }));
            }
          }));
          $scope.$on('$destroy', (function() {
            $docflowActions.remove(container);
          }));
        })
      };
    })
  ]);

  module.directive('docflowActionsContainerContent', [
    '$docflowActions', '$compile', (function($docflowActions, $compile) {
      return {
        restrict: 'EA',
        require: '^docflowActionsContainer',
        transclude: 'element',
        link: (function($scope, element, attrs, container, $transclude) {
          container.contentTransclude = $transclude;
          container.contentElement = element;
        })
      };
    })
  ]);

  module.directive('docflowActionsAutohide', [
    '$docflowActions', 'docflowActionDoneHideDelayTime', '$timeout', (function($docflowActions, docflowActionDoneHideDelayTime, $timeout) {
      return {
        restrict: 'EA',
        scope: true,
        link: (function($scope, element, attrs) {
          $scope.action.showClose = false;
          $timeout((function() {
            var _ref;
            if ((_ref = $scope.action) != null) {
              _ref.hide();
            }
          }), docflowActionDoneHideDelayTime);
        })
      };
    })
  ]);

  module.factory('$docflowActions', [
    '$docflowConfig', '$docflowUtils', 'docflowActionInProgressShowAfterDelayTime', 'docflowActionDoneHideDelayTime', '$rootScope', '$timeout', (function($docflowConfig, $docflowUtils, docflowActionInProgressShowAfterDelayTime, docflowActionDoneHideDelayTime, $rootScope, $timeout) {
      var Action, containers, currentAction, currentContainer, preserveActionResultOverUIStateTransitionCount, selectContainer;
      currentAction = null;
      currentContainer = null;
      containers = [];
      selectContainer = (function(action) {
        var cont, container, _i, _len;
        if (currentAction !== action && currentContainer) {
          currentContainer.setAction(null);
          currentContainer = null;
        }
        currentAction = action;
        if (containers.length > 0) {
          container = containers[0];
          if (action.state !== 'failed') {
            for (_i = 0, _len = containers.length; _i < _len; _i++) {
              cont = containers[_i];
              if (!cont.errorOnly) {
                container = cont;
                break;
              }
            }
          }
          if (currentContainer !== container) {
            if (currentContainer) {
              currentContainer.setAction(null);
            }
            (currentContainer = container).setAction(action);
          }
        }
      });
      preserveActionResultOverUIStateTransitionCount = 0;
      return {
        containers: containers,
        Action: (Action = (function() {
          var _class;

          function Action() {
            return _class.apply(this, arguments);
          }

          _class = (function(docId, name, promise) {
            var forceShowMessage, message, sid, title, _ref, _ref1, _ref2, _ref3;
            if (!docId) {
              throw Error('id argument is missing');
            }
            if (!name) {
              throw Error('name argument is missing');
            }
            if (!promise) {
              throw Error('promise argument is missing');
            }
            sid = $docflowUtils.splitFullId(docId);
            title = (_ref = $docflowConfig.docs[sid.docType]) != null ? (_ref1 = _ref.actions) != null ? (_ref2 = _ref1[name]) != null ? _ref2.title : void 0 : void 0 : void 0;
            if (!title) {
              title = name;
            }
            message = (_ref3 = $docflowConfig.messages['actionProgress']) != null ? _ref3.replace('{{title}}', title) : void 0;
            this.name = name;
            this.title = title || name;
            this.state = 'progress';
            this.message = null;
            this.promise = promise;
            this.showClose = false;
            if (message) {
              this.forceShowMessage = forceShowMessage = ((function(_this) {
                return function() {
                  if (_this.state === 'progress') {
                    _this.message = message;
                  }
                };
              })(this));
              $timeout(forceShowMessage, docflowActionInProgressShowAfterDelayTime);
            }
            promise.then(((function(_this) {
              return function(doneMessage) {
                var _ref4;
                _this.state = 'done';
                if (_this.name === 'list') {
                  _this.message = doneMessage;
                } else if (doneMessage) {
                  _this.showClose = true;
                  _this.message = doneMessage;
                } else {
                  $timeout((function() {
                    _this.hide();
                  }), docflowActionDoneHideDelayTime);
                  _this.message = (_ref4 = $docflowConfig.messages['actionDone']) != null ? _ref4.replace('{{title}}', _this.title) : void 0;
                }
              };
            })(this)), ((function(_this) {
              return function(failedMessage) {
                var _ref4;
                _this.state = 'failed';
                _this.showClose = true;
                _this.message = failedMessage ? _this.message = failedMessage : _this.message = (_ref4 = $docflowConfig.messages['actionFailed']) != null ? _ref4.replace('{{title}}', _this.title) : void 0;
              };
            })(this)));
          });

          Action.prototype.hide = (function() {
            if (currentAction === this) {
              if (currentContainer != null) {
                currentContainer.setAction(null);
              }
            }
          });

          return Action;

        })()),
        setPreserveActionResultOverUIStateTransition: (function() {
          preserveActionResultOverUIStateTransitionCount = 4;
        }),
        add: (function(container) {
          containers.unshift(container);
          if (currentAction && currentContainer) {
            currentContainer.setAction(null);
            currentContainer = null;
          }
          if (preserveActionResultOverUIStateTransitionCount > 0 && currentAction) {
            if (--preserveActionResultOverUIStateTransitionCount === 0) {
              currentContainer = container;
              container.setAction(currentAction);
            }
          }
        }),
        remove: (function(container) {
          var p;
          if ((p = containers.indexOf(container)) >= 0) {
            containers.splice(p, 1);
            if (currentContainer === container) {
              currentContainer = null;
              if (currentAction.state !== 'failed') {
                selectContainer(currentAction);
              }
            }
          }
        }),
        send: (function(action) {
          selectContainer(action);
          action.promise.then((function() {
            return selectContainer(action);
          }), (function() {
            return selectContainer(action);
          }));
        })
      };
    })
  ]);

}).call(this);
