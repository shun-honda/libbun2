
function TestArray(a: int[]) {
	assert(a[0] == 0);
	assert(a.size() == 1);
	a[0] = 1;
	a.add(2);
	return a;
}

function Test(a: int[]) {
	var b = TestArray(a);
	println("#IntArray a: " + b);
	assert(b[0] == 1);
	assert(b.size() == 2);
}

function ArrayOperation() {
	var a: int[] = [0,1,2];
	a.add(3);
	a.insert(1, 9);
	assert(a.size() == 5);
	assert(a[0] == 0);
	assert(a[1] == 9);
	assert(a[2] == 1);
	assert(a[4] == 3);
	//a.clear(2);
	//assert(a.size() == 2);
	//assert(a[1] == 9);
}

export function main() {
	var a = [0];
	Test(a);
	Test([0]);
	var b: int[] = [];
	b.add(0);
	Test(b);
	ArrayOperation();
}

