(function() {
  var getList, getQueryFromUrl, module, putQueryToUrl;

  module = angular.module('docflow.ui.table', ['ui.bootstrap.pagination', 'docflow.ui.actions', 'docflow.ui.utils']);

  module.constant('docflowPagingSize', 7);

  getQueryFromUrl = (function(locationSearch, urlPrefix) {
    var k, query, v;
    query = {};
    for (k in locationSearch) {
      v = locationSearch[k];
      if (k.substring(0, urlPrefix.length) === urlPrefix) {
        query[k.substring(urlPrefix.length)] = decodeURI(v);
      }
    }
    return query;
  });

  putQueryToUrl = (function($docflowUtils, locationSearch, query, urlPrefix) {
    var k, n, v;
    for (k in query) {
      v = query[k];
      if (k !== 'c') {
        n = urlPrefix + k;
        if (angular.isObject(v)) {
          locationSearch[n] = v.id;
        } else if (v && !(k === 'p' && v === 1)) {
          locationSearch[n] = v;
        } else {
          delete locationSearch[n];
        }
      }
    }
    return locationSearch;
  });

  getList = (function($scope, $http, $docflowActions, $docflowUtils, $docflowConfig, $q, docType, pagingSize, params, query, changes) {
    var args, c, deferred, k, p, page, v;
    args = new Array;
    for (p in params) {
      v = params[p];
      if (angular.isDefined(v)) {
        args.push("" + p + "=" + ($docflowUtils.encodeUriQuery(v, true)));
      }
    }
    for (k in query) {
      if (!(k === 'c' || (params != null ? params.hasOwnProperty(k) : void 0))) {
        v = changes && changes.hasOwnProperty(k) ? changes[k] : query[k];
        if (angular.isDefined(v) && v !== null) {
          if (angular.isObject(v)) {
            args.push("" + k + "=" + ($docflowUtils.encodeUriQuery(v.id, true)));
          } else {
            args.push("" + k + "=" + ($docflowUtils.encodeUriQuery(v, true)));
          }
        }
      }
    }
    page = (changes != null ? changes['p'] : void 0) || query['p'] || 1;
    if (page >= 0) {
      c = page + Math.round(pagingSize / 2);
      if (c <= pagingSize) {
        c = pagingSize + 1;
      }
      args.push("c=" + c);
    }
    if ($docflowActions) {
      deferred = $q.defer();
      $docflowActions.send(new $docflowActions.Action(docType, 'list', deferred.promise));
    }
    return $http({
      method: 'GET',
      url: "/api/list/" + docType + "?" + (args.join('&')) + "&" + $docflowConfig.apiParams
    }).then((function(resp) {
      if (deferred) {
        if (!resp.data.hasOwnProperty('code') || resp.data.code === 'Ok') {
          deferred.resolve(resp.data.message);
        } else {
          deferred.reject(resp.data.message);
        }
      }
      $docflowUtils.processDocumentsListBeforeEditing(resp.data.list);
      return resp.data;
    }));
  });

  module.directive('docflowTable', [
    '$http', '$docflowActions', '$docflowUtils', '$docflowConfig', '$q', '$location', 'docflowPagingSize', '$log', (function($http, $docflowActions, $docflowUtils, $docflowConfig, $q, $location, docflowPagingSize, $log) {
      return {
        restrict: 'E',
        replace: false,
        transclude: true,
        scope: true,
        controller: [
          '$scope', '$attrs', (function($scope, $attrs) {
            $scope.urlPrefix = $attrs.urlPrefix;
            $scope.query = $scope.urlPrefix ? getQueryFromUrl($location.search(), $scope.urlPrefix) : $location.search();
            $scope.docType = $attrs.docType;
            $scope.pagingSize = $attrs.pagingSize || docflowPagingSize;
            $scope.params = {};
            $scope.ctrlValues = {};
            $scope.values = {};
            $scope.items = null;
            $scope.$watch('query', (function(n, o) {
              if (n !== o) {
                angular.copy(n, $scope.values);
              }
            }), true);
          })
        ],
        link: (function($scope, element, attrs, controller, $transclude) {
          var value;
          if (!$transclude) {
            $log.error('Missing transclution content');
          }
          if (attrs.data) {
            value = $scope.$eval(attrs.data);
            if (!angular.isObject(value)) {
              throw new Error('Invalid docflow-table data: #{attrs.data}');
            }
            angular.extend($scope, value);
            if (attrs.onLoad) {
              $scope.$eval(attrs.onLoad, {
                'list': $scope.list
              });
            }
          } else if (attrs.params) {
            value = $scope.$eval(attrs.params);
            if (!angular.isObject(value)) {
              throw new Error('Invalid docflow-table params: #{attrs.params}');
            }
            $scope.params = value;
            $scope.$evalAsync('update()');
          } else {
            $scope.$evalAsync('update()');
          }
          $scope.update = (function(changes) {
            getList($scope, $http, ($scope.urlPrefix ? null : $docflowActions), $docflowUtils, $docflowConfig, $q, $scope.docType, $scope.pagingSize, $scope.params, $scope.query, changes).then(function(update) {
              var list, query;
              $scope.query = query = update.query;
              $scope.list = list = update.list;
              $scope.items = query.p === query.c ? (query.p - 1) * query.s + (list != null ? list.length : void 0) : null;
              if (attrs.onLoad) {
                $scope.$eval(attrs.onLoad, {
                  'list': $scope.list
                });
              }
              $location.search(putQueryToUrl($docflowUtils, $location.search(), $scope.query, $scope.urlPrefix));
              return $location.replace();
            });
          });
          $scope.onSelectPage = (function(page) {
            if (page === $scope.query.c) {
              $scope.update({
                p: -1
              });
            } else {
              $scope.update({
                p: page
              });
            }
          });
          $transclude($scope, function(clone) {
            element.empty();
            return element.append(clone);
          });
        })
      };
    })
  ]);

}).call(this);
