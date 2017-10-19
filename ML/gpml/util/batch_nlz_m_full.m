function nlZ=batch_nlz_m_full(lik_name, hyp, K, mu, m, S, y_batch, R,L,T)
	assert( min(y_batch)>=1 )
	C=max(y_batch);
	n = size(mu,1);
	assert( size(m,1) == n*C )
	assert( size(S,1) == n*C )
	assert( size(S,2) == n*C )

	switch lik_name
	case {'likSoftmax'}
		ll=sampling_m_E(y_batch, m, S, lik_name, 'precompute', generate_static_points_for_mgp(C));
	otherwise
		error('unsupported likelihood')
	end

	kl=0;
	for j=1:C
		idx=(j-1)*n+1:j*n;
		kl=kl+(m(idx,:)-mu)'*(K\(m(idx,:)-mu));
    end
	tmp = R'\(L');
	%nlz excludes const term
	kl=kl-logdet(S)-sum( sum(tmp.*T) );
	nlZ=0.5*kl-sum(ll);



% log(det(A)) for det(A)>0 using the LU decomposition of A
function y = logdet(A)
  [L,U] = lu(A); u = diag(U); 
  if prod(sign(u))~=det(L)
	  %error('det(A)<=0')
	  y= NaN;
  else
	  y = sum(log(abs(u)));
  end
