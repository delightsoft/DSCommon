(function() {
  var module;

  module = angular.module('docflow.widget.datetime', ['docflow.config']);

  $.datepicker.regional['ru'] = {
    closeText: 'Закрыть',
    prevText: '<Пред',
    nextText: 'След>',
    currentText: 'Сегодня',
    monthNames: ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'],
    monthNamesShort: ['Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн', 'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек'],
    dayNames: ['воскресенье', 'понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота'],
    dayNamesShort: ['вск', 'пнд', 'втр', 'срд', 'чтв', 'птн', 'сбт'],
    dayNamesMin: ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'],
    weekHeader: 'Не',
    dateFormat: 'dd.mm.yy',
    firstDay: 1,
    isRTL: false,
    showMonthAfterYear: false,
    yearSuffix: ''
  };

  $.datepicker.setDefaults($.datepicker.regional['ru']);

  $.timepicker.regional['ru'] = {
    timeOnlyTitle: 'Выберите время',
    timeText: 'Время',
    hourText: 'Часы',
    minuteText: 'Минуты',
    secondText: 'Секунды',
    millisecText: 'Миллисекунды',
    timezoneText: 'Часовой пояс',
    currentText: 'Сейчас',
    closeText: 'Закрыть',
    timeFormat: 'HH:mm',
    amNames: ['AM', 'A'],
    pmNames: ['PM', 'P'],
    isRTL: false
  };

  $.timepicker.setDefaults($.timepicker.regional['ru']);

  module.directive('docflowWidgetDate', [
    '$rootScope', '$timeout', (function($rootScope, $timeout) {
      return {
        restrict: 'EA',
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          var input, options, params;
          options = {};
          if (angular.isObject(params = $scope.$eval(attrs.docflowWidgetDate))) {
            angular.extend(options, params);
          }
          input = $('input', element);
          $timeout((function() {
            input.datepicker(options, $.datepicker.regional['ru']);
            input.change((function() {
              var t;
              t = input.datetimepicker('getDate');
              model.$setViewValue(t ? t.getTime() - t.getTimezoneOffset() * 60000 : null);
              $rootScope.$digest();
            }));
            (model.$render = (function() {
              if (input.datetimepicker('getDate') !== model.$viewValue) {
                input.datetimepicker('setDate', model.$viewValue ? new Date(model.$viewValue + (new Date()).getTimezoneOffset() * 60000) : null);
              }
            }))();
          }), 0);
        })
      };
    })
  ]);

  module.directive('docflowWidgetTime', [
    '$rootScope', (function($rootScope) {
      return {
        restrict: 'EA',
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          var input, options, params;
          options = {};
          if (angular.isObject(params = $scope.$eval(attrs.docflowWidgetTime))) {
            angular.extend(options, params);
          }
          input = $('input', element);
          $timeout((function() {
            input.timepicker($.datepicker.regional['ru']);
            input.change((function() {
              var t;
              t = input.datetimepicker('getDate');
              model.$setViewValue(t ? t.getTime() - t.getTimezoneOffset() * 60000 : null);
              $rootScope.$digest();
            }));
            (model.$render = (function() {
              if (input.datetimepicker('getDate') !== model.$viewValue) {
                input.datetimepicker('setDate', model.$viewValue ? new Date(model.$viewValue + (new Date()).getTimezoneOffset() * 60000) : null);
              }
            }))();
          }), 0);
        })
      };
    })
  ]);

  module.directive('docflowWidgetDateTime', [
    '$rootScope', '$timeout', (function($rootScope, $timeout) {
      return {
        restrict: 'EA',
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          var input, options, params;
          options = {};
          if (angular.isObject(params = $scope.$eval(attrs.docflowWidgetDateTime))) {
            angular.extend(options, params);
          }
          input = $('input', element);
          $timeout((function() {
            input.datetimepicker($.datepicker.regional['ru']);
            input.change((function() {
              var t;
              t = input.datetimepicker('getDate');
              model.$setViewValue(t ? t.getTime() - (options.local ? t.getTimezoneOffset() * 60000 : 0) : null);
              $rootScope.$digest();
            }));
            (model.$render = (function() {
              if (input.datetimepicker('getDate') !== model.$viewValue) {
                input.datetimepicker('setDate', model.$viewValue ? new Date(model.$viewValue + (options.local ? (new Date()).getTimezoneOffset() * 60000 : 0)) : null);
              }
            }))();
          }), 0);
        })
      };
    })
  ]);

}).call(this);
