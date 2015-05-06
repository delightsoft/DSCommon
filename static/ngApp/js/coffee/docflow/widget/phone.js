(function() {
  var module;

  module = angular.module('docflow.widget.phone', ['docflow.config']);

  module.filter('phone', function() {
    return function(input) {
      var EditPhone, number, numberEdited;
      number = input || '';
      if (angular.isArray(number)) {
        return;
      }
      numberEdited = number.trim().replace(/[-\s\(\)]/g, '');
      EditPhone = function(num) {
        var area, cod, local;
        cod = "" + num.slice(0, 2);
        area = "(" + num.slice(2, 5) + ")";
        local = "" + num.slice(5, 8) + "-" + num.slice(8);
        num = "" + cod + " " + area + " " + local;
        return num;
      };
      if (numberEdited.length === 12 && ("" + numberEdited.slice(0, 2)) === '+7') {
        return EditPhone(numberEdited);
      } else {
        return number;
      }
    };
  });

}).call(this);
